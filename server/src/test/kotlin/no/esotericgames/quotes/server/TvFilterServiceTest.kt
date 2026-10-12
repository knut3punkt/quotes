package no.esotericgames.quotes.server

import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.IdKey
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteExtractionAttempts
import no.esotericgames.quotes.server.db.QuoteQualityAssessments
import no.esotericgames.quotes.server.db.QuoteQualityRuns
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.TvQuoteFilters
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
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
        val byAuthor = "author=id%3A${fixture.authorId}"

        try {
            val all = tvFilterService.set(byAuthor)
            // The long quote with only a rejected excerpt still shows through it without a quality filter.
            assertEquals(4, all.poolQuotes)
            assertEquals(2, all.poolAsExcerpt)

            val good = tvFilterService.set("$byAuthor&qualityRun=${fixture.runId}&qualityVerdict=good&qualityVerdict=excellent")
            assertEquals(2, good.poolQuotes)
            assertEquals(1, good.poolAsExcerpt)
            assertEquals(fixture.runLabel, good.qualityRunLabel)
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
                    QuoteQualityRuns.deleteWhere { QuoteQualityRuns.id eq fixture.runId }
                    Quotes.deleteWhere { Quotes.id inList fixture.quoteIds }
                    Authors.deleteWhere { Authors.id eq fixture.authorId }
                }
            }
        }
    }

    private class Fixture(
        val authorId: Int,
        val runId: Int,
        val runLabel: String,
        val quoteIds: List<Int>,
        val longQuoteId: Int,
        val goodExcerptId: Int,
        val excellentQuoteId: Int,
    )

    /**
     * One author's quotes, assessed in a fresh run: a long quote with a `good` and a `reject` excerpt, a long quote
     * with only a `reject` excerpt, and short quotes assessed `excellent` and `weak`.
     */
    private fun insertFixture(): Fixture {
        val marker = UUID.randomUUID().toString()
        val authorId = Authors.insert {
            it[name] = "TV filter test $marker"
            it[normalizedName] = "tv filter test $marker"
        }[Authors.id]
        val runLabel = "tv filter test $marker"
        val runId = QuoteQualityRuns.insert {
            it[label] = runLabel
            it[runner] = "external"
            it[methodVersion] = "test"
            it[promptVersion] = "test"
        }[QuoteQualityRuns.id]

        fun quote(text: String): Int = Quotes.insert {
            it[Quotes.text] = text
            it[normalizedText] = "$text $marker"
            it[Quotes.authorId] = authorId
        }[Quotes.id]

        fun assess(quoteId: Int, excerptId: Int?, text: String, verdict: String) {
            QuoteQualityAssessments.insert {
                it[QuoteQualityAssessments.runId] = runId
                it[QuoteQualityAssessments.quoteId] = quoteId
                it[QuoteQualityAssessments.excerptId] = excerptId
                it[subjectKind] = if (excerptId == null) "quote" else "excerpt"
                it[subjectText] = text
                it[subjectHash] = "$marker:$text"
                it[wordCount] = text.split(" ").size
                it[status] = "succeeded"
                it[QuoteQualityAssessments.verdict] = verdict
                it[unresolvedReferences] = emptyList()
                it[problems] = emptyList()
                it[precheckFlags] = emptyList()
            }
        }

        fun excerpt(quoteId: Int, text: String, verdict: String): Int {
            val attemptId = QuoteExtractionAttempts.insert {
                it[QuoteExtractionAttempts.quoteId] = quoteId
                it[extractionMethodVersion] = "test"
                it[promptVersion] = "test"
                it[status] = "succeeded"
            }[QuoteExtractionAttempts.id]
            val excerptId = QuoteExcerpts.insert {
                it[QuoteExcerpts.attemptId] = attemptId
                it[QuoteExcerpts.quoteId] = quoteId
                it[QuoteExcerpts.text] = text
                it[startOffset] = 0
                it[endOffset] = text.length
                it[startUnit] = 0
                it[endUnit] = 0
                it[wordCount] = text.split(" ").size
                it[independenceScore] = 90
                it[completenessScore] = 90
                it[quotabilityScore] = 90
                it[contextFidelityScore] = 90
                it[meetsThresholds] = true
            }[QuoteExcerpts.id]
            assess(quoteId, excerptId, text, verdict)
            return excerptId
        }

        val longText = (1..80).joinToString(" ") { "word$it" } + "."
        val longQuoteId = quote("$longText one")
        val goodExcerptId = excerpt(longQuoteId, "A good excerpt.", "good")
        excerpt(longQuoteId, "A rejected excerpt.", "reject")
        val rejectedOnlyQuoteId = quote("$longText two")
        excerpt(rejectedOnlyQuoteId, "Another rejected excerpt.", "reject")
        val excellentQuoteId = quote("An excellent short quote.")
        assess(excellentQuoteId, null, "An excellent short quote.", "excellent")
        val weakQuoteId = quote("A weak short quote.")
        assess(weakQuoteId, null, "A weak short quote.", "weak")

        return Fixture(
            authorId = authorId,
            runId = runId,
            runLabel = runLabel,
            quoteIds = listOf(longQuoteId, rejectedOnlyQuoteId, excellentQuoteId, weakQuoteId),
            longQuoteId = longQuoteId,
            goodExcerptId = goodExcerptId,
            excellentQuoteId = excellentQuoteId,
        )
    }
}
