package no.esotericgames.quotes.server.interpretation

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteExtractionAttempts
import no.esotericgames.quotes.server.db.QuoteInterpretationAttempts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.interpretation.llm.InferenceOutcome
import no.esotericgames.quotes.server.interpretation.llm.InterpretationClient
import no.esotericgames.quotes.server.interpretation.llm.InterpretationRequest
import no.esotericgames.quotes.server.interpretation.llm.RawInterpretationCandidate
import no.esotericgames.quotes.server.loadDotEnvIntoSystemProperties
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeInterpretationClient(private val outcome: InferenceOutcome) : InterpretationClient {
    override suspend fun generateInterpretations(request: InterpretationRequest) = outcome
}

private val TEST_LLM_CONFIG = InterpretationLlmConfig(
    baseUrl = "http://localhost:8888",
    model = "test-model",
    temperature = 0.5,
    maxOutputTokens = 500,
    requestTimeoutMillis = 5000,
)

class QuoteInterpretationServiceTest {

    // Every DB-touching call in this codebase (even a lookup that finds nothing) needs a live
    // Exposed transaction manager, which only exists once the Ktor application module has started —
    // so, as with QuoteExtractionServiceTest, this is the one test in the suite that needs a real
    // local Postgres connection (via application.conf + the local dev DB credentials), and it also
    // covers per-quote batch independence (a real quote alongside a nonexistent id in one call).
    @Test
    fun `persists validated interpretations for a successful generation, replaces them on re-run, and isolates a not-found id in the same batch`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        val client = createClient { install(ClientContentNegotiation) { json() } }
        client.get("/health") // forces the application (and its DB connection) to start before direct DB access below

        val quoteText = "The unexamined life is not worth living."
        val quoteId = withContext(Dispatchers.IO) {
            suspendTransaction {
                Quotes.insert {
                    it[text] = quoteText
                    it[normalizedText] = quoteText.lowercase()
                }[Quotes.id]
            }
        }
        val missingQuoteId = 999_999

        try {
            val candidate = RawInterpretationCandidate("Ethical", "A close reading of the quotation.", 90, 15)
            val service = QuoteInterpretationService(
                FakeInterpretationClient(InferenceOutcome.Success(listOf(candidate), modelId = "test-model")),
                TEST_LLM_CONFIG,
            )

            val firstRun = service.generateForQuotes(listOf(quoteId, missingQuoteId)).results
            val firstRunByQuoteId = firstRun.associateBy { it.quoteId }
            assertEquals("generated", firstRunByQuoteId.getValue(quoteId).outcome)
            assertEquals(1, firstRunByQuoteId.getValue(quoteId).interpretations.size)
            assertEquals("notFound", firstRunByQuoteId.getValue(missingQuoteId).outcome)
            assertEquals(1L, countAttemptsFor(quoteId))

            // Re-running replaces the previous attempt rather than accumulating a second one.
            val secondRun = service.generateForQuotes(listOf(quoteId)).results.single()
            assertEquals("generated", secondRun.outcome)
            assertEquals(1L, countAttemptsFor(quoteId))
        } finally {
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    Quotes.deleteWhere { Quotes.id eq quoteId } // cascades to quote_interpretation_attempts/quote_interpretations
                }
            }
        }
    }

    @Test
    fun `generates a subject for the whole quote and each qualifying excerpt, and replaces the whole set together on re-run`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        val client = createClient { install(ClientContentNegotiation) { json() } }
        client.get("/health")

        val quoteText = "Long passage. It contains a qualifying excerpt and a non-qualifying one."
        val quoteId = withContext(Dispatchers.IO) {
            suspendTransaction {
                Quotes.insert {
                    it[text] = quoteText
                    it[normalizedText] = quoteText.lowercase()
                }[Quotes.id]
            }
        }

        try {
            val (qualifyingExcerptId, nonQualifyingExcerptId) = withContext(Dispatchers.IO) {
                suspendTransaction {
                    val attemptId = QuoteExtractionAttempts.insert {
                        it[QuoteExtractionAttempts.quoteId] = quoteId
                        it[extractionMethodVersion] = "test"
                        it[promptVersion] = "test"
                        it[status] = "succeeded"
                    }[QuoteExtractionAttempts.id]

                    val qualifying = QuoteExcerpts.insert {
                        it[QuoteExcerpts.attemptId] = attemptId
                        it[QuoteExcerpts.quoteId] = quoteId
                        it[text] = "It contains a qualifying excerpt."
                        it[startOffset] = 0
                        it[endOffset] = 34
                        it[startUnit] = 1
                        it[endUnit] = 1
                        it[wordCount] = 5
                        it[independenceScore] = 90
                        it[completenessScore] = 90
                        it[quotabilityScore] = 90
                        it[contextFidelityScore] = 90
                        it[meetsThresholds] = true
                    }[QuoteExcerpts.id]

                    val nonQualifying = QuoteExcerpts.insert {
                        it[QuoteExcerpts.attemptId] = attemptId
                        it[QuoteExcerpts.quoteId] = quoteId
                        it[text] = "a non-qualifying one."
                        it[startOffset] = 35
                        it[endOffset] = 56
                        it[startUnit] = 2
                        it[endUnit] = 2
                        it[wordCount] = 3
                        it[independenceScore] = 20
                        it[completenessScore] = 20
                        it[quotabilityScore] = 20
                        it[contextFidelityScore] = 20
                        it[meetsThresholds] = false
                    }[QuoteExcerpts.id]

                    qualifying to nonQualifying
                }
            }

            val candidate = RawInterpretationCandidate("Ethical", "A close reading of the quotation.", 90, 15)
            val service = QuoteInterpretationService(
                FakeInterpretationClient(InferenceOutcome.Success(listOf(candidate), modelId = "test-model")),
                TEST_LLM_CONFIG,
            )

            val firstRun = service.generateForQuotes(listOf(quoteId)).results.single()
            assertEquals("generated", firstRun.outcome)
            val firstRunByExcerptId = firstRun.interpretations.associateBy { it.excerptId }
            assertEquals(setOf(null, qualifyingExcerptId), firstRunByExcerptId.keys)
            assertEquals(2L, countAttemptsFor(quoteId)) // one subject: whole quote + one qualifying excerpt

            // Re-running replaces the whole subject set together, not just the changed subject.
            val secondRun = service.generateForQuotes(listOf(quoteId)).results.single()
            assertEquals("generated", secondRun.outcome)
            val secondRunByExcerptId = secondRun.interpretations.associateBy { it.excerptId }
            assertEquals(setOf(null, qualifyingExcerptId), secondRunByExcerptId.keys)
            assertEquals(2L, countAttemptsFor(quoteId)) // still 2, not 4 — no accumulation across runs
            assertEquals(false, nonQualifyingExcerptId in secondRunByExcerptId.keys)
        } finally {
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    // cascades to quote_extraction_attempts/quote_excerpts/quote_interpretation_attempts/quote_interpretations
                    Quotes.deleteWhere { Quotes.id eq quoteId }
                }
            }
        }
    }

    private suspend fun countAttemptsFor(quoteId: Int): Long = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteInterpretationAttempts.selectAll().where { QuoteInterpretationAttempts.quoteId eq quoteId }.count()
        }
    }
}
