package no.esotericgames.quotes.server.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

data class QuoteFilter(
    val authorId: Int? = null,
    val verified: Boolean? = null,
    val language: String? = null,
    val search: String? = null,
    val page: Int = 1,
    val pageSize: Int = 50,
)

class QuoteAdminService {

    suspend fun listQuotes(filter: QuoteFilter): PagedQuotesResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            val pageSize = filter.pageSize.coerceIn(1, 2000)
            val page = filter.page.coerceAtLeast(1)

            var query = Quotes.leftJoin(Authors).leftJoin(Sources).selectAll()
            filter.authorId?.let { id -> query = query.andWhere { Quotes.authorId eq id } }
            filter.verified?.let { verified -> query = query.andWhere { Quotes.verified eq verified } }
            filter.language?.let { language -> query = query.andWhere { Quotes.language eq language } }
            filter.search?.trim()?.takeIf { it.isNotEmpty() }?.let { term ->
                val pattern = "%${term.lowercase()}%"
                query = query.andWhere { Quotes.text.lowerCase() like pattern }
            }

            val total = query.count()
            val rows = query
                .orderBy(Quotes.id to SortOrder.DESC)
                .limit(pageSize)
                .offset((page - 1).toLong() * pageSize)
                .toList()

            val quoteIds = rows.map { it[Quotes.id] }
            val excerptsByQuoteId = if (quoteIds.isEmpty()) {
                emptyMap()
            } else {
                QuoteExcerpts.selectAll().where { QuoteExcerpts.quoteId inList quoteIds }
                    .map { it.toQuoteExcerptResponse() }
                    .groupBy { it.quoteId }
            }
            val items = rows.map { it.toQuoteListItemResponse(excerptsByQuoteId[it[Quotes.id]].orEmpty()) }

            PagedQuotesResponse(items = items, total = total, page = page, pageSize = pageSize)
        }
    }

}

private fun ResultRow.toQuoteListItemResponse(excerpts: List<QuoteExcerptResponse>) = QuoteListItemResponse(
    id = this[Quotes.id],
    text = this[Quotes.text],
    authorId = this[Quotes.authorId],
    authorName = this.getOrNull(Authors.name),
    sourceId = this[Quotes.sourceId],
    sourceTitle = this.getOrNull(Sources.title),
    sourceDetail = this[Quotes.sourceDetail],
    verified = this[Quotes.verified],
    language = this[Quotes.language],
    excerpts = excerpts,
)

private fun ResultRow.toQuoteExcerptResponse() = QuoteExcerptResponse(
    id = this[QuoteExcerpts.id],
    quoteId = this[QuoteExcerpts.quoteId],
    text = this[QuoteExcerpts.text],
    startOffset = this[QuoteExcerpts.startOffset],
    endOffset = this[QuoteExcerpts.endOffset],
    independence = this[QuoteExcerpts.independenceScore],
    completeness = this[QuoteExcerpts.completenessScore],
    quotability = this[QuoteExcerpts.quotabilityScore],
    contextualFidelity = this[QuoteExcerpts.contextFidelityScore],
    reason = this[QuoteExcerpts.reason].orEmpty(),
    meetsThresholds = this[QuoteExcerpts.meetsThresholds],
)
