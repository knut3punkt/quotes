package no.esotericgames.quotes.server.admin

import kotlinx.serialization.Serializable

@Serializable
data class ExtractQuoteExcerptsRequest(val quoteIds: List<Int>)

@Serializable
data class QuoteExcerptResponse(
    val id: Int,
    val quoteId: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val independence: Int,
    val completeness: Int,
    val quotability: Int,
    val contextualFidelity: Int,
    val reason: String,
    val meetsThresholds: Boolean,
)

@Serializable
data class QuoteExtractionResult(
    val quoteId: Int,
    val outcome: String, // "extracted" | "skippedTooShort" | "noExcerptsFound" | "failed" | "notFound"
    val excerpts: List<QuoteExcerptResponse>,
)

@Serializable
data class ExtractionBatchResponse(val results: List<QuoteExtractionResult>)
