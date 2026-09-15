package no.esotericgames.quotes.server.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ImportedQuoteResponse(
    val id: Int,
    val provider: String,
    val providerQuoteId: String,
    val rawText: String,
    val rawAuthor: String?,
    val rawSourceLocation: String?,
    val sourceId: Int?,
    val rawPayload: JsonElement,
    val importedAt: String,
    val processingStatus: String,
    val quoteId: Int?,
    val sourceConfidence: String?,
    val reviewedBy: String?,
    val reviewedAt: String?,
    val reviewNote: String?,
    val duplicateOfId: Int?,
    val language: String,
    val possibleDuplicateOfId: Int?,
)

@Serializable
data class PagedImportedQuotesResponse(
    val items: List<ImportedQuoteResponse>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)

@Serializable
data class UpdateImportedQuoteStatusRequest(
    val status: String,
    val reviewedBy: String? = null,
    val reviewNote: String? = null,
)

@Serializable
data class BulkUpdateImportedQuoteStatusRequest(
    val ids: List<Int>,
    val status: String,
    val reviewedBy: String? = null,
    val reviewNote: String? = null,
)

@Serializable
data class BulkDeleteImportedQuotesRequest(val ids: List<Int>)

@Serializable
data class BulkActionResponse(val succeededIds: List<Int>, val failedIds: List<Int>)

@Serializable
data class NewSourceRequest(
    val title: String,
    val typeCode: String,
    val year: Int? = null,
    val url: String? = null,
    val citationUnit: String? = null,
    val license: String? = null,
    val attributionText: String? = null,
    val translation: String? = null,
)

@Serializable
data class ApproveImportedQuoteRequest(
    val authorId: Int? = null,
    val newAuthorName: String? = null,
    val sourceId: Int? = null,
    val newSource: NewSourceRequest? = null,
    val sourceDetail: String? = null,
    val text: String? = null,
    val verified: Boolean = false,
    val reviewedBy: String? = null,
)

@Serializable
data class QuoteResponse(
    val id: Int,
    val text: String,
    val authorId: Int?,
    val sourceId: Int?,
    val sourceDetail: String?,
    val verified: Boolean,
    val language: String,
)

@Serializable
data class AuthorResponse(val id: Int, val name: String, val birthYear: Int?, val deathYear: Int?, val wikidataQid: String?)

@Serializable
data class SourceResponse(
    val id: Int,
    val title: String,
    val typeCode: String,
    val year: Int?,
    val url: String?,
    val citationUnit: String?,
    val license: String?,
    val attributionText: String?,
    val translation: String?,
)

@Serializable
data class SourceTypeResponse(val code: String, val description: String)

@Serializable
data class AuthorEnrichmentResponse(
    val checked: Int,
    val enriched: Int,
    val skipped: Int,
)
