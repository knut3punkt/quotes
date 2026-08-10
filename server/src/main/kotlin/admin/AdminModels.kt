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
    val rawPayload: JsonElement,
    val importedAt: String,
    val processingStatus: String,
    val quoteId: Int?,
    val sourceConfidence: String?,
)

@Serializable
data class UpdateImportedQuoteStatusRequest(val status: String)

@Serializable
data class ApproveImportedQuoteRequest(
    val authorId: Int? = null,
    val newAuthorName: String? = null,
    val sourceId: Int? = null,
    val sourceDetail: String? = null,
    val text: String? = null,
    val verified: Boolean = false,
)

@Serializable
data class QuoteResponse(
    val id: Int,
    val text: String,
    val authorId: Int,
    val sourceId: Int?,
    val sourceDetail: String?,
    val verified: Boolean,
)

@Serializable
data class AuthorResponse(val id: Int, val name: String)

@Serializable
data class SourceResponse(val id: Int, val title: String)
