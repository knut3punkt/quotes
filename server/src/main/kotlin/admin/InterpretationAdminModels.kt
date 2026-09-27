package no.esotericgames.quotes.server.admin

import kotlinx.serialization.Serializable

@Serializable
data class GenerateQuoteInterpretationsRequest(val quoteIds: List<Int>)

@Serializable
data class QuoteInterpretationResponse(
    val id: Int,
    val quoteId: Int,
    val excerptId: Int? = null,
    val lens: String,
    val interpretation: String,
    val textualSupport: Int,
    val speculativeness: Int,
)

@Serializable
data class QuoteInterpretationResult(
    val quoteId: Int,
    val outcome: String, // "generated" | "noInterpretationsFound" | "failed" | "notFound"
    val interpretations: List<QuoteInterpretationResponse>,
)

@Serializable
data class GenerateQuoteInterpretationsResponse(val results: List<QuoteInterpretationResult>)
