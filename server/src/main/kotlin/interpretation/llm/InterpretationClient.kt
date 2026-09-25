package no.esotericgames.quotes.server.interpretation.llm

import no.esotericgames.quotes.server.extraction.llm.GenerationSettings

/**
 * The boundary that keeps the local inference runtime replaceable, mirroring
 * [no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionClient] (per
 * docs/features/quote-interpretations.md's "Reuse the existing local LLM infrastructure" guidance).
 * Speaks only in plain prompt text and a runtime-agnostic result — never in OpenAI/llama.cpp-specific
 * request/response shapes — so swapping the runtime never requires touching interpretation domain
 * logic. [GenerationSettings] is reused as-is from the extraction feature since it is already
 * runtime-agnostic and has no excerpt-specific fields.
 */
interface InterpretationClient {
    suspend fun generateInterpretations(request: InterpretationRequest): InferenceOutcome
}

data class InterpretationRequest(
    val systemPrompt: String,
    val userContent: String,
    val generation: GenerationSettings,
)

data class RawInterpretationCandidate(
    val lens: String,
    val interpretation: String,
    val textualSupport: Int,
    val speculativeness: Int,
)

sealed interface InferenceOutcome {
    data class Success(val candidates: List<RawInterpretationCandidate>, val modelId: String?) : InferenceOutcome
    data class ConnectionFailure(val message: String) : InferenceOutcome
    data class MalformedResponse(val message: String) : InferenceOutcome
}
