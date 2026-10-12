package no.esotericgames.quotes.server.quality

import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.esotericgames.quotes.server.admin.CreateQualityRunRequest
import no.esotericgames.quotes.server.admin.QuoteAdminService
import no.esotericgames.quotes.server.admin.QuoteFilter
import no.esotericgames.quotes.server.admin.UploadAssessmentItem
import no.esotericgames.quotes.server.db.QuoteQualityAssessments
import no.esotericgames.quotes.server.db.QuoteQualityRuns
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.importing.sha256Hex
import no.esotericgames.quotes.server.loadDotEnvIntoSystemProperties
import no.esotericgames.quotes.server.quality.llm.QualityOutcome
import no.esotericgames.quotes.server.quality.llm.QualityRequest
import no.esotericgames.quotes.server.quality.llm.QualityResponseDto
import no.esotericgames.quotes.server.quality.llm.QuoteQualityClient
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val LLM_CONFIG = QualityLlmConfig("http://localhost:8888", "test-model", 0.1, 900, 5000)

private fun dto(verdict: String) = QualityResponseDto(
    reading = "r", insight = "i", standsAlone = 5, completeness = 5, substance = 4, expression = 4, assessment = "a", verdict = verdict,
)

private class FixedQualityClient(private val outcome: QualityOutcome) : QuoteQualityClient {
    override suspend fun assess(request: QualityRequest) = outcome
}

class QuoteQualityServiceTest {

    // Needs a real database connection, like QuoteExtractionServiceTest.
    @Test
    fun `runs are kept side by side, re-assessing replaces within a run, and assessments outlive their quote`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        client.get("/health")

        val text = "A quality-test quote that exists only for the duration of this test, ${System.nanoTime()}."
        val quoteId = withContext(Dispatchers.IO) {
            suspendTransaction {
                Quotes.insert {
                    it[Quotes.text] = text
                    it[normalizedText] = text.lowercase()
                }[Quotes.id]
            }
        }
        val runIds = mutableListOf<Int>()
        try {
            val good = QuoteQualityService(FixedQualityClient(QualityOutcome.Success(dto("good"), "test-model")), LLM_CONFIG, 55)
            val weak = QuoteQualityService(FixedQualityClient(QualityOutcome.Success(dto("weak"), "test-model")), LLM_CONFIG, 55)
            val down = QuoteQualityService(FixedQualityClient(QualityOutcome.ConnectionFailure("refused")), LLM_CONFIG, 55)

            val runA = good.createRun(CreateQualityRunRequest(label = "test A", runner = "llm")).id.also(runIds::add)
            val runB = good.createRun(CreateQualityRunRequest(label = "test B", runner = "llm")).id.also(runIds::add)

            assertEquals("assessed", good.assessForQuotes(runA, listOf(quoteId)).results.single().outcome)
            weak.assessForQuotes(runA, listOf(quoteId)) // replaces the row in run A
            good.assessForQuotes(runB, listOf(quoteId))
            assertEquals(listOf("weak"), verdictsIn(runA))
            assertEquals(listOf("good"), verdictsIn(runB))

            // A failed assessment is stored as failed, not skipped.
            assertEquals("failed", down.assessForQuotes(runA, listOf(quoteId)).results.single().outcome)
            assertEquals(listOf(null), verdictsIn(runA))

            // The quote list filters on the run.
            val listed = QuoteAdminService().listQuotes(QuoteFilter(qualityRunId = runB, qualityVerdicts = setOf("good"), search = text))
            assertEquals(listOf(quoteId), listed.items.map { it.id })
            assertEquals("good", listed.items.single().quality.single().verdict)

            // An llm run takes no uploads; an external run does, with the same validation.
            val external = good.createRun(CreateQualityRunRequest(label = "test external", runner = "external", modelId = "claude")).id
                .also(runIds::add)
            assertFailsWith<IllegalStateException> { good.upload(runA, emptyList()) }
            assertFailsWith<IllegalStateException> { good.assessForQuotes(external, listOf(quoteId)) }
            val response = Json.encodeToJsonElement(QualityResponseDto.serializer(), dto("excellent")).jsonObject
            val invalid = JsonObject(response + ("verdict" to Json.parseToJsonElement("\"superb\"")))
            val uploaded = good.upload(
                external,
                listOf(
                    UploadAssessmentItem(quoteId, null, sha256Hex(text), response),
                    UploadAssessmentItem(quoteId, null, "stale-hash", response),
                    UploadAssessmentItem(quoteId, null, sha256Hex(text), invalid),
                ),
            )
            assertEquals(1, uploaded.stored)
            assertEquals(2, uploaded.rejected.size)
            assertTrue(good.subjects(external, QuoteFilter(search = text), onlyMissing = true).isEmpty())

            val comparison = good.compare(runB, external, disagreementLimit = 10)
            assertEquals(1, comparison.matched)

            // Unapproving deletes the quote; its assessments stay, unlinked.
            withContext(Dispatchers.IO) { suspendTransaction { Quotes.deleteWhere { Quotes.id eq quoteId } } }
            val orphaned = withContext(Dispatchers.IO) {
                suspendTransaction { QuoteQualityAssessments.selectAll().where { QuoteQualityAssessments.runId eq runB }.single() }
            }
            assertNull(orphaned[QuoteQualityAssessments.quoteId])
            assertEquals(text, orphaned[QuoteQualityAssessments.subjectText])
        } finally {
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    QuoteQualityRuns.deleteWhere { QuoteQualityRuns.id inList runIds } // cascades to the assessments
                    Quotes.deleteWhere { Quotes.id eq quoteId }
                }
            }
        }
    }

    private suspend fun verdictsIn(runId: Int): List<String?> = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteQualityAssessments.selectAll().where { QuoteQualityAssessments.runId eq runId }
                .map { it[QuoteQualityAssessments.verdict] }
        }
    }
}
