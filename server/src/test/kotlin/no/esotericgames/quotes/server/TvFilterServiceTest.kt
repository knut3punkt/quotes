package no.esotericgames.quotes.server

import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.IdKey
import no.esotericgames.quotes.server.db.TvQuoteFilters
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TvFilterServiceTest {

    @Test
    fun `a stored query drops paging and parses like the admin quote list`() {
        val query = normalizeQuery("?author=id%3A7&page=3&pageSize=50&qualityRun=2&qualityVerdict=good&qualityVerdict=excellent")

        assertEquals("author=id%3A7&qualityRun=2&qualityVerdict=good&qualityVerdict=excellent", query)
        val filter = parseTvQuoteFilter(query)
        assertEquals(setOf<IdKey>(IdKey.Id(7)), filter.authors)
        assertEquals(2, filter.qualityRunId)
        assertEquals(setOf("good", "excellent"), filter.qualityVerdicts)
    }

    @Test
    fun `an invalid query is refused`() {
        assertFailsWith<IllegalArgumentException> { parseTvQuoteFilter("qualityVerdict=good") }
        assertFailsWith<IllegalArgumentException> { parseTvQuoteFilter("qualityRun=1&qualityVerdict=splendid") }
    }

    // Needs a real database connection, like PublicQuoteServiceTest. The fixture quotes share a fresh author, and
    // every filter here includes that author, so other data in the dev database doesn't matter.
    @Test
    fun `the TV shows only the excerpts and full quotes whose own verdicts match`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        client.get("/health") // starts the application, and with it the database connection

        val previousFilter = withContext(Dispatchers.IO) {
            suspendTransaction { TvQuoteFilters.selectAll().singleOrNull()?.get(TvQuoteFilters.query) }
        }
        val fixture = withContext(Dispatchers.IO) { suspendTransaction { insertFixture() } }
        val publicQuoteService = PublicQuoteService(PublicQuoteConfig(maxFullQuoteWords = 55))
        val tvFilterService = TvFilterService(publicQuoteService)
        val byAuthor = "author=id%3A${fixture.data.authorId}"

        try {
            val all = tvFilterService.set(byAuthor)
            // The long quote with only a rejected excerpt still shows through it without a quality filter.
            assertEquals(4, all.poolQuotes)
            assertEquals(2, all.poolAsExcerpt)

            val good = tvFilterService.set("$byAuthor&qualityRun=${fixture.data.runId}&qualityVerdict=good&qualityVerdict=excellent")
            assertEquals(2, good.poolQuotes)
            assertEquals(1, good.poolAsExcerpt)
            assertEquals(fixture.data.runLabel, good.qualityRunLabel)
            repeat(5) {
                val shown = publicQuoteService.randomQuotes(PublicQuoteService.MAX_COUNT).map { it.id to it.excerptId }.toSet()
                assertEquals(setOf(fixture.longQuoteId to fixture.goodExcerptId, fixture.excellentQuoteId to null), shown)
            }

            tvFilterService.clear()
            assertNull(tvFilterService.get())
        } finally {
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    TvQuoteFilters.deleteAll()
                    previousFilter?.let { query ->
                        TvQuoteFilters.insert {
                            it[id] = TvQuoteFilters.SINGLE_ROW_ID
                            it[TvQuoteFilters.query] = query
                        }
                    }
                    fixture.data.delete()
                }
            }
        }
    }

    private class Fixture(
        val data: QualityFixtures,
        val longQuoteId: Int,
        val goodExcerptId: Int,
        val excellentQuoteId: Int,
    )

    /**
     * One author's quotes, assessed in a fresh run: a long quote with a `good` and a `reject` excerpt, a long quote
     * with only a `reject` excerpt, and short quotes assessed `excellent` and `weak`.
     */
    private fun insertFixture(): Fixture {
        val data = QualityFixtures()
        val longText = (1..80).joinToString(" ") { "word$it" } + "."
        val longQuoteId = data.quote("$longText one")
        val goodExcerptId = data.assessedExcerpt(longQuoteId, "A good excerpt.", "good")
        data.assessedExcerpt(longQuoteId, "A rejected excerpt.", "reject")
        val rejectedOnlyQuoteId = data.quote("$longText two")
        data.assessedExcerpt(rejectedOnlyQuoteId, "Another rejected excerpt.", "reject")
        val excellentQuoteId = data.quote("An excellent short quote.")
        data.assess(excellentQuoteId, null, "An excellent short quote.", "excellent")
        val weakQuoteId = data.quote("A weak short quote.")
        data.assess(weakQuoteId, null, "A weak short quote.", "weak")
        return Fixture(data, longQuoteId = longQuoteId, goodExcerptId = goodExcerptId, excellentQuoteId = excellentQuoteId)
    }
}
