package no.esotericgames.quotes.server.tagging

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.AddQuoteTagRequest
import no.esotericgames.quotes.server.admin.MergeTagRequest
import no.esotericgames.quotes.server.admin.QuoteTagResponse
import no.esotericgames.quotes.server.admin.TagAdminService
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteExtractionAttempts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Tags
import no.esotericgames.quotes.server.loadDotEnvIntoSystemProperties
import no.esotericgames.quotes.server.tagging.llm.InferenceOutcome
import no.esotericgames.quotes.server.tagging.llm.RawTagCandidate
import no.esotericgames.quotes.server.tagging.llm.RawTagging
import no.esotericgames.quotes.server.tagging.llm.TaggingClient
import no.esotericgames.quotes.server.tagging.llm.TaggingRequest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeTaggingClient(var outcome: InferenceOutcome) : TaggingClient {
    val userContents = mutableListOf<String>()

    override suspend fun generateTags(request: TaggingRequest): InferenceOutcome {
        userContents += request.userContent
        return outcome
    }
}

class QuoteTaggingServiceTest {

    // Like QuoteInterpretationServiceTest, this needs the local dev Postgres. Tag names carry a random
    // prefix so the test never collides with real vocabulary, and the test deletes its own tags at the end.
    @Test
    fun `tags the whole quote or only its excerpts, respects admin edits and merges, and isolates failures`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        val client = createClient { install(ClientContentNegotiation) { json() } }
        client.get("/health") // starts the application, and with it the DB connection

        val prefix = "zzt" + UUID.randomUUID().toString().replace("-", "").take(10)
        val alpha = "$prefix alpha"
        val beta = "$prefix beta"
        val gamma = "$prefix gamma"
        val quoteText = "Long passage. It gets one qualifying excerpt later."
        val quoteId = insertQuote(quoteText)
        val missingQuoteId = 999_999

        try {
            val firstSuccessOutcome = InferenceOutcome.Success(
                RawTagging(
                    concepts = listOf(
                        RawTagCandidate(alpha, "broad", 3, "text"),
                        RawTagCandidate(beta, "specific", 2, "interpretation"),
                    ),
                    moods = listOf(RawTagCandidate("$prefix calm", null, 2, "text")),
                    motifs = listOf(RawTagCandidate("$prefix lamps", null, 1, "text")),
                ),
                modelId = "test-model",
            )
            val fake = FakeTaggingClient(firstSuccessOutcome)
            val service = QuoteTaggingService(fake, TaggingConfig(DEFAULT_TAGGING_LLM_CONFIG, DEFAULT_TAGGING_POLICY))
            val admin = TagAdminService()

            // First run, no excerpts yet: the whole quote gets all four tags.
            val firstRun = service.generateForQuotes(listOf(quoteId, missingQuoteId)).results.associateBy { it.quoteId }
            assertEquals("notFound", firstRun.getValue(missingQuoteId).outcome)
            val firstTags = firstRun.getValue(quoteId)
            assertEquals("tagged", firstTags.outcome)
            assertEquals(4, firstTags.tags.count { it.excerptId == null })
            assertTrue(firstTags.tags.any { it.name == "$prefix lamp" && it.facet == "motif" }) // plural folded

            // Admin removes beta and adds gamma by hand.
            admin.rejectAssignment(firstTags.tags.single { it.name == beta }.assignmentId)
            admin.addTagToQuote(quoteId, AddQuoteTagRequest(facet = "concept", name = gamma))

            // Second run: beta stays removed; gamma survives.
            val secondTags = service.generateForQuotes(listOf(quoteId)).results.single().tags
            assertFalse(secondTags.any { it.name == beta })
            assertTrue(secondTags.any { it.name == gamma && it.origin == "admin" })

            // Merge alpha into gamma. A third run that still says "alpha" lands on gamma, without duplicates.
            val alphaTagId = secondTags.first { it.name == alpha }.tagId
            val gammaTagId = secondTags.first { it.name == gamma }.tagId
            val merged = admin.mergeTag(alphaTagId, MergeTagRequest(intoTagId = gammaTagId))
            assertEquals(listOf(alpha), merged.aliases)

            val thirdTags = service.generateForQuotes(listOf(quoteId)).results.single().tags
            assertFalse(thirdTags.any { it.name == alpha })
            assertEquals(1, thirdTags.count { it.name == gamma })

            // A failed call reports failure and leaves the earlier tags in place.
            fake.outcome = InferenceOutcome.ConnectionFailure("llama-server down")
            val failedRun = service.generateForQuotes(listOf(quoteId)).results.single()
            assertEquals("failed", failedRun.outcome)
            assertEquals(thirdTags.map(QuoteTagResponse::assignmentId).toSet(), failedRun.tags.map { it.assignmentId }.toSet())

            // Once the quote has a qualifying excerpt, only the excerpt is tagged. The whole quote's LLM tags
            // are removed; the admin-added gamma stays on the whole quote.
            val excerptId = insertQualifyingExcerpt(quoteId, quoteText, "It gets one qualifying excerpt later.")
            fake.outcome = firstSuccessOutcome
            fake.userContents.clear()
            val excerptRun = service.generateForQuotes(listOf(quoteId)).results.single()
            assertEquals("tagged", excerptRun.outcome)
            assertEquals(1, fake.userContents.size)
            assertTrue(fake.userContents.single().startsWith("QUOTE TO TAG:"))
            assertEquals(listOf(gamma), excerptRun.tags.filter { it.excerptId == null }.map { it.name })
            assertEquals(
                setOf(gamma, beta, "$prefix calm", "$prefix lamp"),
                excerptRun.tags.filter { it.excerptId == excerptId }.map { it.name }.toSet(),
            )
        } finally {
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    Quotes.deleteWhere { Quotes.id eq quoteId } // cascades to excerpts, attempts and assignments
                    Tags.deleteWhere { Tags.normalizedName like "$prefix%" }
                }
            }
        }
    }

    private suspend fun insertQuote(quoteText: String): Int = withContext(Dispatchers.IO) {
        suspendTransaction {
            Quotes.insert {
                it[text] = quoteText
                it[normalizedText] = quoteText.lowercase()
            }[Quotes.id]
        }
    }

    private suspend fun insertQualifyingExcerpt(quoteId: Int, quoteText: String, excerptText: String): Int =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                val attemptId = QuoteExtractionAttempts.insert {
                    it[QuoteExtractionAttempts.quoteId] = quoteId
                    it[extractionMethodVersion] = "test"
                    it[promptVersion] = "test"
                    it[status] = "succeeded"
                }[QuoteExtractionAttempts.id]
                QuoteExcerpts.insert {
                    it[QuoteExcerpts.attemptId] = attemptId
                    it[QuoteExcerpts.quoteId] = quoteId
                    it[text] = excerptText
                    it[startOffset] = quoteText.indexOf(excerptText)
                    it[endOffset] = quoteText.length
                    it[startUnit] = 2
                    it[endUnit] = 2
                    it[wordCount] = 5
                    it[independenceScore] = 100
                    it[completenessScore] = 100
                    it[quotabilityScore] = 100
                    it[contextFidelityScore] = 100
                    it[meetsThresholds] = true
                }[QuoteExcerpts.id]
            }
        }
}
