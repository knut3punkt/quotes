package no.esotericgames.quotes.server

import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.TvDisplayQualityRuns
import no.esotericgames.quotes.server.db.TvDisplaySettings
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TvDisplayServiceTest {

    // Needs a real database connection, like PublicQuoteServiceTest.
    @Test
    fun `the quality overlay shows the shown subject's assessment in each chosen run, only while on`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        client.get("/health") // starts the application, and with it the database connection

        val previous = withContext(Dispatchers.IO) {
            suspendTransaction {
                val showQuality = TvDisplaySettings.selectAll().singleOrNull()?.get(TvDisplaySettings.showQuality)
                showQuality to TvDisplayQualityRuns.selectAll().map { it[TvDisplayQualityRuns.runId] }
            }
        }
        val service = TvDisplayService()
        lateinit var data: QualityFixtures
        var otherRunId = 0
        val otherRunLabel = "other run ${System.nanoTime()}"
        var quoteId = 0
        var excerptId = 0
        var unassessedQuoteId = 0
        withContext(Dispatchers.IO) {
            suspendTransaction {
                data = QualityFixtures()
                otherRunId = data.newRun(otherRunLabel)
                quoteId = data.quote("A quote with an excerpt, and a verdict of its own.")
                data.assess(quoteId, null, "A quote with an excerpt, and a verdict of its own.", "weak", problems = listOf("FLAT"))
                excerptId = data.excerpt(quoteId, "An excerpt.")
                data.assess(quoteId, excerptId, "An excerpt.", "good", precheckFlags = listOf("SHORT"))
                data.assess(
                    quoteId, excerptId, "An excerpt.", "reject",
                    problems = listOf("FRAGMENT"), precheckFlags = listOf("SHORT"), inRun = otherRunId,
                )
                unassessedQuoteId = data.quote("A quote the run never assessed.")
            }
        }

        try {
            service.update(TvDisplaySettingsRequest(showQuality = false, qualityRunIds = listOf(data.runId)))
            assertNull(service.qualityFor(quoteId, excerptId))

            val settings = service.update(
                TvDisplaySettingsRequest(showQuality = true, qualityRunIds = listOf(otherRunId, data.runId)),
            )
            // Runs are listed oldest first, whatever order they were picked in.
            assertEquals(
                listOf(TvDisplayRun(data.runId, data.runLabel), TvDisplayRun(otherRunId, otherRunLabel)),
                settings.qualityRuns,
            )

            assertEquals(
                TvQualityResponse(
                    runs = listOf(
                        TvRunQuality(data.runLabel, "succeeded", "good", problems = emptyList()),
                        TvRunQuality(otherRunLabel, "succeeded", "reject", problems = listOf("FRAGMENT")),
                    ),
                    precheckFlags = listOf("SHORT"),
                ),
                service.qualityFor(quoteId, excerptId),
            )
            assertEquals(
                TvQualityResponse(
                    runs = listOf(
                        TvRunQuality(data.runLabel, "succeeded", "weak", problems = listOf("FLAT")),
                        TvRunQuality(otherRunLabel, "notAssessed", null, problems = emptyList()),
                    ),
                    precheckFlags = emptyList(),
                ),
                service.qualityFor(quoteId, excerptId = null),
            )
            assertEquals(
                listOf("notAssessed", "notAssessed"),
                service.qualityFor(unassessedQuoteId, excerptId = null)?.runs?.map { it.status },
            )

            assertFailsWith<IllegalArgumentException> {
                service.update(TvDisplaySettingsRequest(showQuality = true, qualityRunIds = emptyList()))
            }
        } finally {
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    TvDisplaySettings.deleteAll()
                    TvDisplayQualityRuns.deleteAll()
                    val (showQuality, runIds) = previous
                    if (showQuality != null) {
                        TvDisplaySettings.insert {
                            it[id] = TvDisplaySettings.SINGLE_ROW_ID
                            it[TvDisplaySettings.showQuality] = showQuality
                        }
                    }
                    TvDisplayQualityRuns.batchInsert(runIds) { runId -> this[TvDisplayQualityRuns.runId] = runId }
                    data.delete()
                }
            }
        }
    }
}
