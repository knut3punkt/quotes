package no.esotericgames.quotes.server.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.ImportedQuotes
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.SourceTypes
import no.esotericgames.quotes.server.db.Sources
import no.esotericgames.quotes.server.importing.SourceDescriptor
import no.esotericgames.quotes.server.importing.findOrCreateSource
import no.esotericgames.quotes.server.importing.normalizeAuthorName
import no.esotericgames.quotes.server.importing.normalizeQuoteText
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.charLength
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

private val MANUAL_STATUSES = setOf("pending", "rejected", "duplicate")

private const val NO_AUTHOR_LABEL = "(No author)"
private const val NO_SOURCE_LABEL = "(No source)"

data class ImportedQuoteFilter(
    val statuses: Set<String>? = null,
    val provider: String? = null,
    val sourceConfidence: String? = null,
    val search: String? = null,
    /** Matches any of these; empty means no author filter. */
    val authors: Set<AuthorKey> = emptySet(),
    /** Matches any of these; empty means no source filter. */
    val sources: Set<SourceKey> = emptySet(),
    /** `true` for only rows flagged as possible duplicates, `false` for only unflagged rows. */
    val possibleDuplicate: Boolean? = null,
    /** Inclusive lower bound on the import time. */
    val importedFrom: OffsetDateTime? = null,
    /** Exclusive upper bound on the import time. */
    val importedBefore: OffsetDateTime? = null,
    /** Exclusive lower bound on the raw text's length in characters. */
    val minLength: Int? = null,
    /** Exclusive upper bound on the raw text's length in characters. */
    val maxLength: Int? = null,
    val page: Int = 1,
    val pageSize: Int = 100,
)

class ImportedQuoteAdminService {

    suspend fun listImportedQuotes(filter: ImportedQuoteFilter): PagedImportedQuotesResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            val pageSize = filter.pageSize.coerceIn(1, 2000)
            val page = filter.page.coerceAtLeast(1)

            val query = ImportedQuotes.selectAll().applyFilter(filter)
            val total = query.count()
            val items = query
                .orderBy(ImportedQuotes.importedAt to SortOrder.DESC, ImportedQuotes.id to SortOrder.DESC)
                .limit(pageSize)
                .offset((page - 1).toLong() * pageSize)
                .map { it.toImportedQuoteResponse() }

            PagedImportedQuotesResponse(items = items, total = total, page = page, pageSize = pageSize)
        }
    }

    /** Every row matching [filter] (ignoring paging), with just what the admin's bulk actions need to decide on. */
    suspend fun selection(filter: ImportedQuoteFilter): List<ImportedQuoteSelectionItem> = withContext(Dispatchers.IO) {
        suspendTransaction {
            ImportedQuotes
                .select(
                    ImportedQuotes.id,
                    ImportedQuotes.processingStatus,
                    ImportedQuotes.rawAuthor,
                    ImportedQuotes.sourceId,
                    ImportedQuotes.quoteId,
                )
                .applyFilter(filter)
                .orderBy(ImportedQuotes.importedAt to SortOrder.DESC, ImportedQuotes.id to SortOrder.DESC)
                .map {
                    ImportedQuoteSelectionItem(
                        id = it[ImportedQuotes.id],
                        processingStatus = it[ImportedQuotes.processingStatus],
                        approvable = !it[ImportedQuotes.rawAuthor].isNullOrBlank() || it[ImportedQuotes.sourceId] != null,
                        quoteId = it[ImportedQuotes.quoteId],
                    )
                }
        }
    }

    /** Choices and counts for the imported-quotes filters, over all imported rows. */
    suspend fun filterOptions(): ImportedQuoteFilterOptionsResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            val rowCount = ImportedQuotes.id.count()

            val providers = ImportedQuotes.select(ImportedQuotes.provider)
                .withDistinct()
                .map { it[ImportedQuotes.provider] }
                .sorted()

            val statusCounts = ImportedQuotes.select(ImportedQuotes.processingStatus, rowCount)
                .groupBy(ImportedQuotes.processingStatus)
                .associate { it[ImportedQuotes.processingStatus] to it[rowCount] }

            val trimmedAuthor = ImportedQuotes.rawAuthor.trim()
            val authorCounts = ImportedQuotes.select(trimmedAuthor, rowCount)
                .groupBy(trimmedAuthor)
                .map { authorKeyOf(it[trimmedAuthor]) to it[rowCount] }
            val authors = buildFilterOptions(authorCounts, NO_AUTHOR_LABEL) { key ->
                (parseAuthorKey(key) as AuthorKey.Name).name
            }

            val trimmedSourceTitle = ImportedQuotes.rawSourceTitle.trim()
            val sourceCounts = ImportedQuotes.select(ImportedQuotes.sourceId, trimmedSourceTitle, rowCount)
                .groupBy(ImportedQuotes.sourceId, trimmedSourceTitle)
                .map { sourceKeyOf(it[ImportedQuotes.sourceId], it[trimmedSourceTitle]) to it[rowCount] }
            val linkedSourceIds = sourceCounts.mapNotNull { (key, _) -> (parseSourceKey(key) as? SourceKey.Linked)?.sourceId }
            val sourceTitles = if (linkedSourceIds.isEmpty()) {
                emptyMap()
            } else {
                Sources.select(Sources.id, Sources.title).where { Sources.id inList linkedSourceIds }
                    .associate { it[Sources.id] to it[Sources.title] }
            }
            val sources = buildFilterOptions(sourceCounts, NO_SOURCE_LABEL) { key ->
                when (val sourceKey = parseSourceKey(key)) {
                    is SourceKey.Linked -> sourceTitles[sourceKey.sourceId] ?: "Source ${sourceKey.sourceId}"
                    is SourceKey.Suggested -> "${sourceKey.title} (suggested)"
                    SourceKey.None -> NO_SOURCE_LABEL
                }
            }

            ImportedQuoteFilterOptionsResponse(
                providers = providers,
                statusCounts = statusCounts,
                authors = authors,
                sources = sources,
            )
        }
    }

    suspend fun updateStatus(id: Int, status: String, reviewedBy: String?, reviewNote: String?): ImportedQuoteResponse {
        require(status in MANUAL_STATUSES) { "status must be one of $MANUAL_STATUSES" }
        return withContext(Dispatchers.IO) {
            suspendTransaction {
                val updatedRows = ImportedQuotes.update({ ImportedQuotes.id eq id }) {
                    it[processingStatus] = status
                    it[ImportedQuotes.reviewedBy] = reviewedBy
                    it[reviewedAt] = OffsetDateTime.now()
                    it[ImportedQuotes.reviewNote] = reviewNote
                }
                if (updatedRows == 0) throw NoSuchElementException("imported quote $id not found")
                ImportedQuotes.selectAll().where { ImportedQuotes.id eq id }.first().toImportedQuoteResponse()
            }
        }
    }

    suspend fun bulkUpdateStatus(
        ids: List<Int>,
        status: String,
        reviewedBy: String?,
        reviewNote: String?,
    ): BulkActionResponse {
        require(status in MANUAL_STATUSES) { "status must be one of $MANUAL_STATUSES" }
        if (ids.isEmpty()) return BulkActionResponse(succeededIds = emptyList(), failedIds = emptyList())
        return withContext(Dispatchers.IO) {
            suspendTransaction {
                val existingIds = ImportedQuotes.selectAll().where { ImportedQuotes.id inList ids }
                    .map { it[ImportedQuotes.id] }
                    .toSet()
                if (existingIds.isNotEmpty()) {
                    ImportedQuotes.update({ ImportedQuotes.id inList existingIds }) {
                        it[processingStatus] = status
                        it[ImportedQuotes.reviewedBy] = reviewedBy
                        it[reviewedAt] = OffsetDateTime.now()
                        it[ImportedQuotes.reviewNote] = reviewNote
                    }
                }
                BulkActionResponse(
                    succeededIds = ids.filter { it in existingIds },
                    failedIds = ids.filterNot { it in existingIds },
                )
            }
        }
    }

    suspend fun approve(id: Int, request: ApproveImportedQuoteRequest): QuoteResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            val importedRow = ImportedQuotes.selectAll().where { ImportedQuotes.id eq id }.firstOrNull()
                ?: throw NoSuchElementException("imported quote $id not found")

            if (importedRow[ImportedQuotes.processingStatus] != "pending") {
                throw IllegalStateException(
                    "imported quote $id is already ${importedRow[ImportedQuotes.processingStatus]}; cannot approve again",
                )
            }

            val authorId = request.authorId
                ?: request.newAuthorName?.let { name -> findOrCreateAuthor(name) }
                ?: importedRow[ImportedQuotes.rawAuthor]?.let { name -> findOrCreateAuthor(name) }

            val sourceId = request.sourceId
                ?: request.newSource?.let { findOrCreateSource(it.toDescriptor()) }
                ?: importedRow[ImportedQuotes.sourceId]

            if (authorId == null && sourceId == null) {
                throw IllegalArgumentException("either an author or a source is required")
            }

            val quoteText = request.text ?: importedRow[ImportedQuotes.rawText]
            val sourceDetailValue = request.sourceDetail ?: importedRow[ImportedQuotes.rawSourceLocation]
            val language = importedRow[ImportedQuotes.language]
            val insertedQuoteId = Quotes.insert {
                it[text] = quoteText
                it[Quotes.authorId] = authorId
                it[Quotes.sourceId] = sourceId
                it[sourceDetail] = sourceDetailValue
                it[Quotes.language] = language
                it[normalizedText] = normalizeQuoteText(quoteText)
            }[Quotes.id]

            ImportedQuotes.update({ ImportedQuotes.id eq id }) {
                it[quoteId] = insertedQuoteId
                it[processingStatus] = "approved"
                it[reviewedBy] = request.reviewedBy
                it[reviewedAt] = OffsetDateTime.now()
            }

            QuoteResponse(
                id = insertedQuoteId,
                text = quoteText,
                authorId = authorId,
                sourceId = sourceId,
                sourceDetail = sourceDetailValue,
                language = language,
            )
        }
    }

    suspend fun delete(id: Int): Unit = withContext(Dispatchers.IO) {
        suspendTransaction {
            val deletedRows = ImportedQuotes.deleteWhere { ImportedQuotes.id eq id }
            if (deletedRows == 0) throw NoSuchElementException("imported quote $id not found")
        }
    }

    suspend fun bulkDelete(ids: List<Int>): BulkActionResponse {
        if (ids.isEmpty()) return BulkActionResponse(succeededIds = emptyList(), failedIds = emptyList())
        return withContext(Dispatchers.IO) {
            suspendTransaction {
                val existingIds = ImportedQuotes.selectAll().where { ImportedQuotes.id inList ids }
                    .map { it[ImportedQuotes.id] }
                    .toSet()
                if (existingIds.isNotEmpty()) {
                    ImportedQuotes.deleteWhere { ImportedQuotes.id inList existingIds }
                }
                BulkActionResponse(
                    succeededIds = ids.filter { it in existingIds },
                    failedIds = ids.filterNot { it in existingIds },
                )
            }
        }
    }

    suspend fun listAuthors(): List<AuthorResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            Authors.selectAll().orderBy(Authors.name to SortOrder.ASC).map {
                AuthorResponse(
                    id = it[Authors.id],
                    name = it[Authors.name],
                    birthYear = it[Authors.birthYear],
                    deathYear = it[Authors.deathYear],
                    wikidataQid = it[Authors.wikidataQid],
                )
            }
        }
    }

    suspend fun listSources(): List<SourceResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            Sources.selectAll().orderBy(Sources.title to SortOrder.ASC).map { it.toSourceResponse() }
        }
    }

    suspend fun createSource(request: NewSourceRequest): SourceResponse {
        val id = findOrCreateSource(request.toDescriptor())
        return withContext(Dispatchers.IO) {
            suspendTransaction {
                Sources.selectAll().where { Sources.id eq id }.first().toSourceResponse()
            }
        }
    }

    suspend fun listSourceTypes(): List<SourceTypeResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            SourceTypes.selectAll().orderBy(SourceTypes.code to SortOrder.ASC).map {
                SourceTypeResponse(code = it[SourceTypes.code], description = it[SourceTypes.description])
            }
        }
    }

    private fun findOrCreateAuthor(name: String): Int {
        val normalized = normalizeAuthorName(name)
        val existing = Authors.selectAll().where { Authors.normalizedName eq normalized }.firstOrNull()
        if (existing != null) return existing[Authors.id]
        return Authors.insert {
            it[Authors.name] = name
            it[normalizedName] = normalized
        }[Authors.id]
    }

}

private fun Query.applyFilter(filter: ImportedQuoteFilter): Query {
    var query = this
    filter.statuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
        query = query.andWhere { ImportedQuotes.processingStatus inList statuses }
    }
    filter.provider?.let { provider -> query = query.andWhere { ImportedQuotes.provider eq provider } }
    filter.sourceConfidence?.let { confidence ->
        query = query.andWhere { ImportedQuotes.sourceConfidence eq confidence }
    }
    filter.search?.trim()?.takeIf { it.isNotEmpty() }?.let { term ->
        val pattern = "%${term.lowercase()}%"
        query = query.andWhere {
            (ImportedQuotes.rawText.lowerCase() like pattern) or (ImportedQuotes.rawAuthor.lowerCase() like pattern)
        }
    }
    if (filter.authors.isNotEmpty()) {
        query = query.andWhere { filter.authors.map(::authorCondition).reduce { acc, op -> acc or op } }
    }
    if (filter.sources.isNotEmpty()) {
        query = query.andWhere { filter.sources.map(::sourceCondition).reduce { acc, op -> acc or op } }
    }
    filter.possibleDuplicate?.let { flagged ->
        query = query.andWhere {
            if (flagged) ImportedQuotes.possibleDuplicateOfId.isNotNull() else ImportedQuotes.possibleDuplicateOfId.isNull()
        }
    }
    filter.importedFrom?.let { from -> query = query.andWhere { ImportedQuotes.importedAt greaterEq from } }
    filter.importedBefore?.let { before -> query = query.andWhere { ImportedQuotes.importedAt less before } }
    filter.minLength?.let { length -> query = query.andWhere { ImportedQuotes.rawText.charLength() greater length } }
    filter.maxLength?.let { length -> query = query.andWhere { ImportedQuotes.rawText.charLength() less length } }
    return query
}

private fun authorCondition(key: AuthorKey): Op<Boolean> = when (key) {
    AuthorKey.None -> ImportedQuotes.rawAuthor.isNull() or (ImportedQuotes.rawAuthor.trim() eq "")
    is AuthorKey.Name -> ImportedQuotes.rawAuthor.trim() eq key.name
}

private fun sourceCondition(key: SourceKey): Op<Boolean> = when (key) {
    SourceKey.None -> ImportedQuotes.sourceId.isNull() and
        (ImportedQuotes.rawSourceTitle.isNull() or (ImportedQuotes.rawSourceTitle.trim() eq ""))
    is SourceKey.Linked -> ImportedQuotes.sourceId eq key.sourceId
    is SourceKey.Suggested -> ImportedQuotes.sourceId.isNull() and (ImportedQuotes.rawSourceTitle.trim() eq key.title)
}

private fun NewSourceRequest.toDescriptor() = SourceDescriptor(
    title = title,
    typeCode = typeCode,
    year = year,
    url = url,
    citationUnit = citationUnit,
    license = license,
    attributionText = attributionText,
    translation = translation,
)

private fun ResultRow.toImportedQuoteResponse() = ImportedQuoteResponse(
    id = this[ImportedQuotes.id],
    provider = this[ImportedQuotes.provider],
    providerQuoteId = this[ImportedQuotes.providerQuoteId],
    rawText = this[ImportedQuotes.rawText],
    rawAuthor = this[ImportedQuotes.rawAuthor],
    rawSourceLocation = this[ImportedQuotes.rawSourceLocation],
    rawSourceTitle = this[ImportedQuotes.rawSourceTitle],
    rawSourceYear = this[ImportedQuotes.rawSourceYear],
    sourceId = this[ImportedQuotes.sourceId],
    rawPayload = this[ImportedQuotes.rawPayload],
    importedAt = this[ImportedQuotes.importedAt].toString(),
    processingStatus = this[ImportedQuotes.processingStatus],
    quoteId = this[ImportedQuotes.quoteId],
    sourceConfidence = this[ImportedQuotes.sourceConfidence],
    reviewedBy = this[ImportedQuotes.reviewedBy],
    reviewedAt = this[ImportedQuotes.reviewedAt]?.toString(),
    reviewNote = this[ImportedQuotes.reviewNote],
    duplicateOfId = this[ImportedQuotes.duplicateOfId],
    language = this[ImportedQuotes.language],
    possibleDuplicateOfId = this[ImportedQuotes.possibleDuplicateOfId],
)

private fun ResultRow.toSourceResponse() = SourceResponse(
    id = this[Sources.id],
    title = this[Sources.title],
    typeCode = this[Sources.typeCode],
    year = this[Sources.year],
    url = this[Sources.url],
    citationUnit = this[Sources.citationUnit],
    license = this[Sources.license],
    attributionText = this[Sources.attributionText],
    translation = this[Sources.translation],
)
