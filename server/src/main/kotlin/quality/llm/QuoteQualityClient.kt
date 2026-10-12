package no.esotericgames.quotes.server.quality.llm

import no.esotericgames.quotes.server.extraction.llm.GenerationSettings

/**
 * The boundary that keeps the inference runtime replaceable, mirroring
 * [no.esotericgames.quotes.server.interpretation.llm.InterpretationClient]: plain prompt text in,
 * a runtime-agnostic outcome out.
 */
interface QuoteQualityClient {
    suspend fun assess(request: QualityRequest): QualityOutcome
}

data class QualityRequest(
    val systemPrompt: String,
    val userContent: String,
    val generation: GenerationSettings,
)

sealed interface QualityOutcome {
    data class Success(val response: QualityResponseDto, val modelId: String?) : QualityOutcome
    data class ConnectionFailure(val message: String) : QualityOutcome
    data class MalformedResponse(val message: String) : QualityOutcome
}
