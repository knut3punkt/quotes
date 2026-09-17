package no.esotericgames.quotes.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import org.jetbrains.exposed.v1.core.Random
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

class PublicQuoteService {

    // Not filtered by `verified` yet: there's no review workflow populating that flag today, so
    // filtering on it would make this endpoint return nothing. Reinstate the filter once approved
    // quotes exist (`Quotes.leftJoin(Authors).leftJoin(Sources).selectAll().andWhere { Quotes.verified eq true }`).
    suspend fun randomQuotes(count: Int): List<PublicQuoteResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            val limit = count.coerceIn(1, MAX_COUNT)
            Quotes.leftJoin(Authors).leftJoin(Sources).selectAll()
                .orderBy(Random() to SortOrder.ASC)
                .limit(limit)
                .map { it.toPublicQuoteResponse() }
        }
    }

    companion object {
        const val DEFAULT_COUNT = 20
        const val MAX_COUNT = 50
    }
}

private fun ResultRow.toPublicQuoteResponse() = PublicQuoteResponse(
    id = this[Quotes.id],
    text = this[Quotes.text],
    author = this.getOrNull(Authors.name),
    sourceTitle = this.getOrNull(Sources.title),
    sourceDetail = this[Quotes.sourceDetail],
)
