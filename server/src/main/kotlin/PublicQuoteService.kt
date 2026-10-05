package no.esotericgames.quotes.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.composition.QuoteVisualCandidates
import no.esotericgames.quotes.server.composition.QuoteVisualSelector
import no.esotericgames.quotes.server.composition.QuoteVisuals
import no.esotericgames.quotes.server.composition.selectVisualCandidates
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import org.jetbrains.exposed.v1.core.Random
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.random.Random as KotlinRandom

class PublicQuoteService(
    private val visualSelector: QuoteVisualSelector = QuoteVisualSelector(),
    private val random: KotlinRandom = KotlinRandom.Default,
) {

    /**
     * Random quotes for the TV. A quote with usable excerpts (`meets_thresholds`) is shown as one of
     * them, picked at random; a quote without is shown in full.
     */
    suspend fun randomQuotes(count: Int): List<PublicQuoteResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            val limit = count.coerceIn(1, MAX_COUNT)
            val rows = Quotes.leftJoin(Authors).leftJoin(Sources).selectAll()
                .orderBy(Random() to SortOrder.ASC)
                .limit(limit)
                .toList()
            val quoteIds = rows.map { it[Quotes.id] }
            val excerptsByQuote = QuoteExcerpts.selectAll()
                .where { (QuoteExcerpts.quoteId inList quoteIds) and (QuoteExcerpts.meetsThresholds eq true) }
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
