package no.esotericgames.quotes.server

import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.QualityCustomFlags
import no.esotericgames.quotes.server.db.QuoteQualityAssessments
import no.esotericgames.quotes.server.db.TvDisplaySettings
import no.esotericgames.quotes.server.db.TvQuoteFilters
import no.esotericgames.quotes.server.quality.QualityLlmConfig
import no.esotericgames.quotes.server.quality.QuoteQualityService
import no.esotericgames.quotes.server.quality.llm.QualityOutcome
import no.esotericgames.quotes.server.quality.llm.QualityRequest
import no.esotericgames.quotes.server.quality.llm.QuoteQualityClient
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Human ratings never reach the model. */
private object UnusedQualityClient : QuoteQualityClient {
    override suspend fun assess(request: QualityRequest): QualityOutcome = error("not used")
}

class TvRatingServiceTest {

    // Needs a real database connection, like TvFilterServiceTest. The TV filter limits the pool to the fixture author.
    @Test
    fun `the remote rates what is shown into a human run, only while rating mode is on, and unrated quotes come first`() =
        testApplication {
            loadDotEnvIntoSystemProperties()
            environment { config = ApplicationConfig("application.conf") }
            client.get("/health") // starts the application, and with it the database connection

            val (previousRatingRun, previousFilter) = withContext(Dispatchers.IO) {
                suspendTransaction {
                    activeRatingRunId() to TvQuoteFilters.selectAll().singleOrNull()?.get(TvQuoteFilters.query)
                }
            }
            lateinit var data: QualityFixtures
            var humanRunId = 0
            var fullQuoteId = 0
            var excerptQuoteId = 0
            var excerptId = 0
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    data = QualityFixtures()
                    humanRunId = data.newRun("human ${System.nanoTime()}", runner = "human")
                    fullQuoteId = data.quote("A short quote shown in full.")
                    excerptQuoteId = data.quote("A quote with an excerpt. The rest is never shown.")
                    excerptId = data.excerpt(excerptQuoteId, "A quote with an excerpt.")
                }
            }
            val publicQuoteService = PublicQuoteService(PublicQuoteConfig(maxFullQuoteWords = 55))
            val tvDisplayService = TvDisplayService()
            val qualityService = QuoteQualityService(UnusedQualityClient, QualityLlmConfig("http://unused", "unused", 0.0, 1, 1), 55)
            val service = TvRatingService(tvDisplayService, qualityService)
            val flagLabel = "Preachy ${System.nanoTime()}"
            suspend fun shown() = publicQuoteService.randomQuotes(PublicQuoteService.MAX_COUNT).map { it.id to it.excerptId }.toSet()

            try {
                TvFilterService(publicQuoteService).set("author=id%3A${data.authorId}")
                tvDisplayService.setRatingRun(null)
                assertNull(service.ratingFor(fullQuoteId, null))
                assertFailsWith<IllegalStateException> { service.rate(fullQuoteId, TvRatingRequest(verdict = "good")) }
                assertFailsWith<IllegalStateException> { service.createFlag(flagLabel) }
                assertFailsWith<IllegalArgumentException> { tvDisplayService.setRatingRun(data.runId) } // an external run

                assertEquals(humanRunId, tvDisplayService.setRatingRun(humanRunId).ratingRun?.id)
                val unrated = service.ratingFor(fullQuoteId, null)!!
                assertNull(unrated.verdict)
                assertTrue(unrated.flags.any { it.code == "FRAGMENT" && !it.custom })

                val flag = service.createFlag(flagLabel)
                assertTrue(flag.custom)
                assertEquals(flag, service.createFlag(flagLabel.lowercase())) // the same flag, not a second one
                assertEquals("CLICHE", service.createFlag("cliche").code) // a model problem, not a new flag

                val rated = service.rate(fullQuoteId, TvRatingRequest(verdict = "good", problems = listOf("FLAT", flag.code)))
                assertEquals("good" to listOf("FLAT", flag.code), rated.verdict to rated.problems)
                service.rate(fullQuoteId, TvRatingRequest(verdict = "weak")) // replaces the rating
                assertEquals("weak" to emptyList<String>(), service.ratingFor(fullQuoteId, null)!!.let { it.verdict to it.problems })
                assertEquals(1, ratingsIn(humanRunId))

                assertFailsWith<IllegalArgumentException> {
                    service.rate(fullQuoteId, TvRatingRequest(verdict = "good", problems = listOf("NO_SUCH_FLAG")))
                }
                assertFailsWith<NoSuchElementException> {
                    service.rate(fullQuoteId, TvRatingRequest(excerptId = excerptId, verdict = "good")) // not this quote's
                }

                // The rated full quote is held back while the excerpt is unrated; once both are rated, both come back.
                assertEquals(setOf(excerptQuoteId to excerptId), shown())
                service.rate(excerptQuoteId, TvRatingRequest(excerptId = excerptId, verdict = "excellent"))
                assertEquals(setOf(fullQuoteId to null, excerptQuoteId to excerptId), shown())
            } finally {
                withContext(Dispatchers.IO) {
                    suspendTransaction {
                        TvDisplaySettings.upsert {
                            it[id] = TvDisplaySettings.SINGLE_ROW_ID
                            it[ratingRunId] = previousRatingRun
                        }
                        TvQuoteFilters.deleteWhere { TvQuoteFilters.id eq TvQuoteFilters.SINGLE_ROW_ID }
                        previousFilter?.let { query ->
                            TvQuoteFilters.insert {
                                it[id] = TvQuoteFilters.SINGLE_ROW_ID
                                it[TvQuoteFilters.query] = query
                            }
                        }
                        QualityCustomFlags.deleteWhere { QualityCustomFlags.label eq flagLabel }
                        data.delete()
                    }
                }
            }
        }

    private suspend fun ratingsIn(runId: Int): Long = withContext(Dispatchers.IO) {
        suspendTransaction { QuoteQualityAssessments.selectAll().where { QuoteQualityAssessments.runId eq runId }.count() }
    }
}
