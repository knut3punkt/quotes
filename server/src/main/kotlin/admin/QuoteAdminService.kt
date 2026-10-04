package no.esotericgames.quotes.server.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.ImportedQuotes
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteExtractionAttempts
import no.esotericgames.quotes.server.db.QuoteInterpretationAttempts
import no.esotericgames.quotes.server.db.QuoteInterpretations
import no.esotericgames.quotes.server.db.QuoteTagAssignments
import no.esotericgames.quotes.server.db.QuoteTaggingAttempts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import no.esotericgames.quotes.server.db.Tags
import no.esotericgames.quotes.server.extraction.CONTEXT_SIGNAL_SEPARATOR
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.charLength
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/**
 * Whether a quote has been through an enrichment pipeline (excerpt extraction, interpretation, tagging).
 * [NONE_FOUND] means a run succeeded but produced nothing; [NOT_RUN] covers quotes never run and quotes
 * whose runs all failed, since both still need a run.
 */
enum class EnrichmentFilter(val queryValue: String) {
    HAS("has"),
    NONE_FOUND("none"),
    NOT_RUN("notRun"),
    ;

    companion object {
        fun fromQueryValue(value: String): EnrichmentFilter =
            entries.firstOrNull { it.queryValue == value }
                ?: throw IllegalArgumentException("unknown enrichment filter '$value'")
    }
}

data class QuoteFilter(
    val authorId: Int? = null,
    val sourceId: Int? = null,
    val verified: Boolean? = null,
    val language: String? = null,
    val search: String? = null,
    /** Provider of an import linked to the quote. */
    val provider: String? = null,
    /** Source confidence of an import linked to the quote. */
    val sourceConfidence: String? = null,
    /** Exclusive lower bound on the quote's length in characters. */
    val minLength: Int? = null,
    /** Exclusive upper bound on the quote's length in characters. */
    val maxLength: Int? = null,
    /** Name of an active tag on the quote, in any facet, case-insensitive. */
    val tag: String? = null,
    val excerpts: EnrichmentFilter? = null,
    val interpretations: EnrichmentFilter? = null,
    val tags: EnrichmentFilter? = null,
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
            filter.sourceId?.let { id -> query = query.andWhere { Quotes.sourceId eq id } }
            filter.verified?.let { verified -> query = query.andWhere { Quotes.verified eq verified } }
            filter.language?.let { language -> query = query.andWhere { Quotes.language eq language } }
            filter.search?.trim()?.takeIf { it.isNotEmpty() }?.let { term ->
                val pattern = "%${term.lowercase()}%"
                query = query.andWhere { (Quotes.text.lowerCase() like pattern) or (Authors.name.lowerCase() like pattern) }
            }
            filter.provider?.let { provider ->
                query = query.andWhere { exists(linkedImports().andWhere { ImportedQuotes.provider eq provider }) }
            }
            filter.sourceConfidence?.let { confidence ->
                query = query.andWhere { exists(linkedImports().andWhere { ImportedQuotes.sourceConfidence eq confidence }) }
            }
            filter.minLength?.let { length -> query = query.andWhere { Quotes.text.charLength() greater length } }
            filter.maxLength?.let { length -> query = query.andWhere { Quotes.text.charLength() less length } }
            filter.tag?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                query = query.andWhere {
                    exists(activeTagAssignments().andWhere { Tags.name.lowerCase() eq name.lowercase() })
                }
            }
            filter.excerpts?.let { status ->
                query = query.andWhere {
                    enrichmentCondition(
                        status,
                        results = QuoteExcerpts.select(QuoteExcerpts.id)
                            .where { (QuoteExcerpts.quoteId eq Quotes.id) and (QuoteExcerpts.meetsThresholds eq true) },
                        succeededAttempts = QuoteExtractionAttempts.select(QuoteExtractionAttempts.id)
                            .where { (QuoteExtractionAttempts.quoteId eq Quotes.id) and (QuoteExtractionAttempts.status eq SUCCEEDED) },
                    )
                }
            }
            filter.interpretations?.let { status ->
                query = query.andWhere {
                    enrichmentCondition(
                        status,
                        results = QuoteInterpretations.select(QuoteInterpretations.id)
                            .where { QuoteInterpretations.quoteId eq Quotes.id },
                        succeededAttempts = QuoteInterpretationAttempts.select(QuoteInterpretationAttempts.id)
                            .where {
                                (QuoteInterpretationAttempts.quoteId eq Quotes.id) and
                                    (QuoteInterpretationAttempts.status eq SUCCEEDED)
                            },
                    )
                }
            }
            filter.tags?.let { status ->
                query = query.andWhere {
                    enrichmentCondition(
                        status,
                        results = activeTagAssignments(),
                        succeededAttempts = QuoteTaggingAttempts.select(QuoteTaggingAttempts.id)
                            .where { (QuoteTaggingAttempts.quoteId eq Quotes.id) and (QuoteTaggingAttempts.status eq SUCCEEDED) },
                    )
                }
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
            val interpretationsByQuoteId = if (quoteIds.isEmpty()) {
                emptyMap()
            } else {
                QuoteInterpretations.selectAll().where { QuoteInterpretations.quoteId inList quoteIds }
                    .map { it.toQuoteInterpretationResponse() }
                    .groupBy { it.quoteId }
            }
            val tagsByQuoteId = selectActiveQuoteTags(quoteIds).groupBy { it.quoteId }
            val items = rows.map {
                it.toQuoteListItemResponse(
                    excerptsByQuoteId[it[Quotes.id]].orEmpty(),
                    interpretationsByQuoteId[it[Quotes.id]].orEmpty(),
                    tagsByQuoteId[it[Quotes.id]].orEmpty(),
                )
            }

            PagedQuotesResponse(items = items, total = total, page = page, pageSize = pageSize)
        }
    }

    suspend fun filterOptions(): QuoteFilterOptionsResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            val providers = ImportedQuotes.select(ImportedQuotes.provider)
                .where { ImportedQuotes.quoteId.isNotNull() }
                .withDistinct()
                .map { it[ImportedQuotes.provider] }
                .sorted()
            val languages = Quotes.select(Quotes.language)
                .withDistinct()
                .map { it[Quotes.language] }
                .sorted()
            QuoteFilterOptionsResponse(providers = providers, languages = languages)
        }
    }

    /**
     * Deletes the given quotes and returns their linked imported quotes to `pending`, so they can be
     * reviewed again. Excerpts, interpretations and tags are removed by `ON DELETE CASCADE`.
     */
    suspend fun bulkUnapprove(quoteIds: List<Int>): BulkActionResponse {
        if (quoteIds.isEmpty()) return BulkActionResponse(succeededIds = emptyList(), failedIds = emptyList())
        return withContext(Dispatchers.IO) {
            suspendTransaction {
                val existingIds = Quotes.selectAll().where { Quotes.id inList quoteIds }
                    .map { it[Quotes.id] }
                    .toSet()
                if (existingIds.isNotEmpty()) {
                    // imported_quotes.quote_id has no ON DELETE action, so unlink before deleting.
                    ImportedQuotes.update({ ImportedQuotes.quoteId inList existingIds }) {
                        it[quoteId] = null
                        it[processingStatus] = "pending"
                        it[reviewedBy] = null
                        it[reviewedAt] = OffsetDateTime.now()
                    }
                    Quotes.deleteWhere { Quotes.id inList existingIds }
                }
                BulkActionResponse(
                    succeededIds = quoteIds.filter { it in existingIds },
                    failedIds = quoteIds.filterNot { it in existingIds },
                )
            }
        }
    }

}

private const val SUCCEEDED = "succeeded"

/** Imports linked to the outer query's quote, for use in a correlated `EXISTS`. */
private fun linkedImports(): Query =
    ImportedQuotes.select(ImportedQuotes.id).where { ImportedQuotes.quoteId eq Quotes.id }

/** Non-rejected tag assignments of the outer query's quote, for use in a correlated `EXISTS`. */
private fun activeTagAssignments(): Query =
    QuoteTagAssignments.innerJoin(Tags)
        .select(QuoteTagAssignments.id)
        .where { (QuoteTagAssignments.quoteId eq Quotes.id) and (QuoteTagAssignments.rejected eq false) }

private fun enrichmentCondition(status: EnrichmentFilter, results: Query, succeededAttempts: Query): Op<Boolean> =
    when (status) {
        EnrichmentFilter.HAS -> exists(results)
        EnrichmentFilter.NONE_FOUND -> notExists(results) and exists(succeededAttempts)
        EnrichmentFilter.NOT_RUN -> notExists(results) and notExists(succeededAttempts)
    }

private fun ResultRow.toQuoteListItemResponse(
    excerpts: List<QuoteExcerptResponse>,
    interpretations: List<QuoteInterpretationResponse>,
    tags: List<QuoteTagResponse>,
) = QuoteListItemResponse(
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
    interpretations = interpretations,
    tags = tags,
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
    contextSignals = this[QuoteExcerpts.contextSignals]?.split(CONTEXT_SIGNAL_SEPARATOR).orEmpty(),
    judgeNotes = this[QuoteExcerpts.judgeNotes].orEmpty(),
    meetsThresholds = this[QuoteExcerpts.meetsThresholds],
)

private fun ResultRow.toQuoteInterpretationResponse() = QuoteInterpretationResponse(
    id = this[QuoteInterpretations.id],
    quoteId = this[QuoteInterpretations.quoteId],
    excerptId = this[QuoteInterpretations.excerptId],
    lens = this[QuoteInterpretations.lens],
    interpretation = this[QuoteInterpretations.interpretation],
    textualSupport = this[QuoteInterpretations.textualSupport],
    speculativeness = this[QuoteInterpretations.speculativeness],
)
