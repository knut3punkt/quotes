package no.esotericgames.quotes.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.composition.QuoteVisualCandidates
import no.esotericgames.quotes.server.composition.QuoteVisualSelector
import no.esotericgames.quotes.server.composition.QuoteVisuals
import no.esotericgames.quotes.server.composition.selectVisualCandidates
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import org.jetbrains.exposed.v1.core.Random
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

class PublicQuoteService(private val visualSelector: QuoteVisualSelector = QuoteVisualSelector()) {

    // Not filtered by `verified` yet: there's no review workflow populating that flag today, so
    // filtering on it would make this endpoint return nothing. Reinstate the filter once approved
    // quotes exist (`Quotes.leftJoin(Authors).leftJoin(Sources).selectAll().andWhere { Quotes.verified eq true }`).
    suspend fun randomQuotes(count: Int): List<PublicQuoteResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            val limit = count.coerceIn(1, MAX_COUNT)
            val rows = Quotes.leftJoin(Authors).leftJoin(Sources).selectAll()
                .orderBy(Random() to SortOrder.ASC)
                .limit(limit)
                .toList()
            val candidates = selectVisualCandidates(rows.map { it[Quotes.id] })
            rows.map { row ->
                val quoteCandidates = candidates[row[Quotes.id]] ?: QuoteVisualCandidates.EMPTY
                row.toPublicQuoteResponse(visualSelector.select(quoteCandidates))
            }
        }
    }

    /** A fresh visual selection for one quote, so the TV can re-roll its composition. */
    suspend fun visualsFor(quoteId: Int): QuoteVisuals = withContext(Dispatchers.IO) {
        suspendTransaction {
            if (Quotes.selectAll().where { Quotes.id eq quoteId }.empty()) {
                throw NoSuchElementException("quote $quoteId not found")
            }
            visualSelector.select(selectVisualCandidates(listOf(quoteId))[quoteId] ?: QuoteVisualCandidates.EMPTY)
        }
    }

    companion object {
        const val DEFAULT_COUNT = 20
        const val MAX_COUNT = 50
    }
}

private fun ResultRow.toPublicQuoteResponse(visuals: QuoteVisuals) = PublicQuoteResponse(
    id = this[Quotes.id],
    text = this[Quotes.text],
    author = this.getOrNull(Authors.name),
    sourceTitle = this.getOrNull(Sources.title),
    sourceDetail = this[Quotes.sourceDetail],
    visuals = visuals,
)
