package no.esotericgames.quotes.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.QuoteFilter
import no.esotericgames.quotes.server.admin.applyQuoteFilter
import no.esotericgames.quotes.server.admin.excerptAssessment
import no.esotericgames.quotes.server.admin.qualityMatches
import no.esotericgames.quotes.server.admin.wholeQuoteAssessment
import no.esotericgames.quotes.server.composition.QuoteVisualCandidates
import no.esotericgames.quotes.server.composition.QuoteVisualSelector
import no.esotericgames.quotes.server.composition.QuoteVisuals
import no.esotericgames.quotes.server.composition.selectVisualCandidates
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Random
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.random.Random as KotlinRandom

/** How many quotes the TV can show under a filter, and how many of them it shows as an excerpt. */
data class TvPoolSize(val quotes: Long, val asExcerpt: Long)

class PublicQuoteService(
    private val config: PublicQuoteConfig = PublicQuoteConfig(),
    private val visualSelector: QuoteVisualSelector = QuoteVisualSelector(),
    private val random: KotlinRandom = KotlinRandom.Default,
) {

    /**
     * Random quotes for the TV. A quote with usable excerpts (`meets_thresholds`) is shown as one of
     * them, picked at random; a quote without is shown in full, so it is only eligible when it is
     * short enough to read on screen ([PublicQuoteConfig.maxFullQuoteWords]). While the admin has set a TV
     * preview filter ([activeTvQuoteFilter]), only what matches it is shown (see [ShowableQuotes]). While rating mode is
     * on ([activeRatingRunId]), what the human run has not rated yet comes first: only when nothing unrated is left are
     * rated quotes shown again.
     */
    suspend fun randomQuotes(count: Int): List<PublicQuoteResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            val limit = count.coerceIn(1, MAX_COUNT)
            val filter = activeTvQuoteFilter()
            val (showable, rows) = activeRatingRunId()
                ?.let { ShowableQuotes(filter, config.maxFullQuoteWords, skipAssessedIn = it) }
                ?.let { unrated -> unrated to randomRows(unrated, limit) }
                ?.takeIf { (_, unratedRows) -> unratedRows.isNotEmpty() }
                ?: ShowableQuotes(filter, config.maxFullQuoteWords).let { it to randomRows(it, limit) }
            val quoteIds = rows.map { it[Quotes.id] }
            val excerptsByQuote = QuoteExcerpts.selectAll()
                .where { (QuoteExcerpts.quoteId inList quoteIds) and showable.excerptShowable() }
                .groupBy({ it[QuoteExcerpts.quoteId] }, { ShownExcerpt(it[QuoteExcerpts.id], it[QuoteExcerpts.text]) })
            val shownExcerpts = quoteIds.associateWith { excerptsByQuote[it]?.random(random) }
            val candidates = selectVisualCandidates(shownExcerpts.mapValues { it.value?.id })
            rows.map { row ->
                val quoteId = row[Quotes.id]
                val quoteCandidates = candidates[quoteId] ?: QuoteVisualCandidates.EMPTY
                row.toPublicQuoteResponse(shownExcerpts[quoteId], visualSelector.select(quoteCandidates))
            }
        }
    }

    private fun randomRows(showable: ShowableQuotes, limit: Int): List<ResultRow> =
        showable.restrict(Quotes.leftJoin(Authors).leftJoin(Sources).selectAll())
            .orderBy(Random() to SortOrder.ASC)
            .limit(limit)
            .toList()

    /** How many quotes [randomQuotes] can choose from under [filter]. Must run inside a transaction. */
    fun poolSize(filter: QuoteFilter?): TvPoolSize {
        val showable = ShowableQuotes(filter, config.maxFullQuoteWords)
        val pool = { showable.restrict(Quotes.leftJoin(Authors).leftJoin(Sources).select(Quotes.id)) }
        return TvPoolSize(
            quotes = pool().count(),
            asExcerpt = pool().andWhere { showable.hasShowableExcerpt() }.count(),
        )
    }

    /**
     * A fresh visual selection for one quote, so the TV can re-roll its composition. [excerptId] is the
     * excerpt being shown, as given by [randomQuotes], or null for the full quotation.
     */
    suspend fun visualsFor(quoteId: Int, excerptId: Int?): QuoteVisuals = withContext(Dispatchers.IO) {
        suspendTransaction {
            if (Quotes.selectAll().where { Quotes.id eq quoteId }.empty()) {
                throw NoSuchElementException("quote $quoteId not found")
            }
            if (excerptId != null &&
                QuoteExcerpts.selectAll()
                    .where { (QuoteExcerpts.id eq excerptId) and (QuoteExcerpts.quoteId eq quoteId) }
                    .empty()
            ) {
                throw NoSuchElementException("excerpt $excerptId of quote $quoteId not found")
            }
            val candidates = selectVisualCandidates(mapOf(quoteId to excerptId))
            visualSelector.select(candidates[quoteId] ?: QuoteVisualCandidates.EMPTY)
        }
    }

    companion object {
        const val DEFAULT_COUNT = 20
        const val MAX_COUNT = 50
    }
}

/**
 * What the TV may show, under an optional preview [filter] (docs/features/quote-quality.md, "TV preview filter").
 * The filter's quote-level conditions (author, source, tag, ...) apply to the quote. Its quality conditions apply
 * to the text actually shown: an accepted excerpt is showable when its own assessment matches; a quote without
 * accepted excerpts is showable in full when it is short enough and its whole-quote assessment matches. A quote
 * with accepted excerpts is never shown in full, so one whose excerpts all fail the filter is not shown at all.
 * Without a filter this is the plain rule: an accepted excerpt, or short enough to show in full. With [skipAssessedIn]
 * (rating mode), a subject that run has already assessed is not showable.
 */
private class ShowableQuotes(
    private val filter: QuoteFilter?,
    private val maxFullQuoteWords: Int,
    private val skipAssessedIn: Int? = null,
) {

    private fun notAssessed(assessment: (Int) -> Query): Op<Boolean> = skipAssessedIn?.let { notExists(assessment(it)) } ?: Op.TRUE

    /** Whether the subject behind [assessment] matches the filter's quality conditions; true when there are none. */
    private fun quality(assessment: (Int) -> Query): Op<Boolean> {
        val runId = filter?.qualityRunId ?: return Op.TRUE
        return qualityMatches(filter.qualityVerdicts, filter.qualityFlags) { assessment(runId) } ?: Op.TRUE
    }

    /** For a query over [QuoteExcerpts]. */
    fun excerptShowable(): Op<Boolean> =
        (QuoteExcerpts.meetsThresholds eq true) and quality(::excerptAssessment) and notAssessed(::excerptAssessment)

    /** For a query over [Quotes]. */
    fun hasShowableExcerpt(): Op<Boolean> =
        exists(QuoteExcerpts.select(QuoteExcerpts.id).where { (QuoteExcerpts.quoteId eq Quotes.id) and excerptShowable() })

    /** The receiver must select from [Quotes] joined with [Authors], since the filter's search matches author names. */
    fun restrict(query: Query): Query {
        val filtered = filter
            ?.let { query.applyQuoteFilter(it.copy(qualityVerdicts = emptySet(), qualityFlags = emptySet())) }
            ?: query
        val hasAcceptedExcerpt = QuoteExcerpts.select(QuoteExcerpts.id)
            .where { (QuoteExcerpts.quoteId eq Quotes.id) and (QuoteExcerpts.meetsThresholds eq true) }
        val showableInFull =
            notExists(hasAcceptedExcerpt) and (Quotes.wordCount lessEq maxFullQuoteWords) and quality(::wholeQuoteAssessment) and
                notAssessed(::wholeQuoteAssessment)
        return filtered.andWhere { hasShowableExcerpt() or showableInFull }
    }
}

private data class ShownExcerpt(val id: Int, val text: String)

private fun ResultRow.toPublicQuoteResponse(excerpt: ShownExcerpt?, visuals: QuoteVisuals) = PublicQuoteResponse(
    id = this[Quotes.id],
    excerptId = excerpt?.id,
    text = excerpt?.text ?: this[Quotes.text],
    author = this.getOrNull(Authors.name),
    sourceTitle = this.getOrNull(Sources.title),
    sourceDetail = this[Quotes.sourceDetail],
    visuals = visuals,
)
