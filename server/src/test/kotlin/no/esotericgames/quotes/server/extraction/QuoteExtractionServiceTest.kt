package no.esotericgames.quotes.server.extraction

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.QuoteExtractionAttempts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.extraction.llm.ExcerptJudgeClient
import no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionClient
import no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionRequest
import no.esotericgames.quotes.server.extraction.llm.InferenceOutcome
import no.esotericgames.quotes.server.extraction.llm.FidelityVerdict
import no.esotericgames.quotes.server.extraction.llm.JudgeOutcome
import no.esotericgames.quotes.server.extraction.llm.JudgeRequest
import no.esotericgames.quotes.server.extraction.llm.StandaloneVerdict
import no.esotericgames.quotes.server.extraction.llm.RawExcerptCandidate
import no.esotericgames.quotes.server.loadDotEnvIntoSystemProperties
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeExcerptSelectionClient(private val outcome: InferenceOutcome) : ExcerptSelectionClient {
    override suspend fun selectExcerpts(request: ExcerptSelectionRequest) = outcome
}

private val STRONG_VERDICT = StandaloneVerdict(
    whatItIsAbout = "Counting.",
    unresolvedReferences = emptyList(),
    insight = "Numbers go on.",
    standsAlone = 5,
    completeness = 5,
    quotability = 4,
)

private class FixedVerdictJudgeClient(private val standaloneOutcome: JudgeOutcome<StandaloneVerdict>) : ExcerptJudgeClient {
    override suspend fun judgeStandalone(request: JudgeRequest) = standaloneOutcome
    override suspend fun judgeFidelity(request: JudgeRequest): JudgeOutcome<FidelityVerdict> =
        JudgeOutcome.Success(FidelityVerdict("Counting.", 5, "faithful"), "judge-model")
}

class QuoteExtractionServiceTest {

    // Unlike most tests in this codebase, this one needs a real database connection, so it loads
    // application.conf plus the local dev DB credentials, same as ApplicationTest's DB-backed test.
    @Test
    fun `persists validated excerpts for a successful extraction and replaces them on re-run`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        val client = createClient { install(ClientContentNegotiation) { json() } }
        client.get("/health") // forces the application (and its DB connection) to start before direct DB access below

        val quoteText = "One two three four five six seven eight nine ten. " +
            "Eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty."
        val quoteId = withContext(Dispatchers.IO) {
            suspendTransaction {
                Quotes.insert {
                    it[text] = quoteText
                    it[normalizedText] = quoteText.lowercase()
                }[Quotes.id]
            }
        }

        try {
            val units = segmentSourceIntoUnits(quoteText)
            val llmConfig = ExtractionLlmConfig(
                baseUrl = "http://localhost:8888",
                model = "test-model",
                temperature = 0.1,
                maxOutputTokens = 500,
                requestTimeoutMillis = 5000,
            )
            val config = ExtractionConfig(llm = llmConfig, judge = llmConfig, policy = ExtractionPolicy(minSourceWords = 5))

            val candidate = RawExcerptCandidate(
                startUnit = units.first().id,
                endUnit = units.first().id,
                references = emptyList(),
                coreIdea = "counting",
                reason = "clear",
            )
            val selector = FakeExcerptSelectionClient(InferenceOutcome.Success(listOf(candidate), modelId = "test-model"))

            val service = QuoteExtractionService(selector, FixedVerdictJudgeClient(JudgeOutcome.Success(STRONG_VERDICT, "judge-model")), config)
            val firstRun = service.extractForQuotes(listOf(quoteId)).results.single()
            assertEquals("extracted", firstRun.outcome)
            assertEquals(1, firstRun.excerpts.size)
            assertTrue(firstRun.excerpts.single().meetsThresholds)
            assertEquals(1L, countAttemptsFor(quoteId))

            // Re-running replaces the previous attempt rather than accumulating a second one. A
            // judge-rejected candidate is still persisted (for tuning), just not as passing.
            val rejectingJudge = FixedVerdictJudgeClient(JudgeOutcome.Success(STRONG_VERDICT.copy(unresolvedReferences = listOf("it")), "judge-model"))
            val secondRun = QuoteExtractionService(selector, rejectingJudge, config).extractForQuotes(listOf(quoteId)).results.single()
            assertEquals("noExcerptsFound", secondRun.outcome)
            assertFalse(secondRun.excerpts.single().meetsThresholds)
            assertNull(secondRun.excerpts.single().contextualFidelity)
            assertEquals(1L, countAttemptsFor(quoteId))

            // An unreachable judge fails the whole attempt.
            val unreachableJudge = FixedVerdictJudgeClient(JudgeOutcome.ConnectionFailure("refused"))
            val thirdRun = QuoteExtractionService(selector, unreachableJudge, config).extractForQuotes(listOf(quoteId)).results.single()
            assertEquals("failed", thirdRun.outcome)
            assertEquals(1L, countAttemptsFor(quoteId))
        } finally {
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    Quotes.deleteWhere { Quotes.id eq quoteId } // cascades to quote_extraction_attempts/quote_excerpts
                }
            }
        }
    }

    private suspend fun countAttemptsFor(quoteId: Int): Long = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteExtractionAttempts.selectAll().where { QuoteExtractionAttempts.quoteId eq quoteId }.count()
        }
    }
}
