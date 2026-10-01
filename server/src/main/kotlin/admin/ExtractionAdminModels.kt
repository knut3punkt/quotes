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
    val contextualFidelity: Int?, // null when the fidelity check was skipped (candidate failed the blind review)
    val reason: String,
    val contextSignals: List<String>,
    val judgeNotes: String,
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
