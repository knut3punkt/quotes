package no.esotericgames.quotes.server.extraction.llm

/**
 * The boundary that keeps the local inference runtime replaceable (per
 * docs/features/quote-extraction.md's "llama.cpp integration" section). Speaks only in plain prompt
 * text and a runtime-agnostic result — never in OpenAI/llama.cpp-specific request/response shapes —
 * so swapping the runtime never requires touching extraction domain logic.
 */
interface ExcerptSelectionClient {
    suspend fun selectExcerpts(request: ExcerptSelectionRequest): InferenceOutcome
}

data class GenerationSettings(
    val model: String,
    val temperature: Double,
    val maxOutputTokens: Int,
    // Optional passthrough to a reasoning-capable model/server's "thinking effort" control (e.g.
    // "low"/"medium"/"high"). Left null by default, in which case no such field is sent at all and
    // the model/server's own default behavior applies. The exact wire field a given local
    // llama-server build honors varies by model family — verify before relying on this.
    val reasoningEffort: String? = null,
)

data class ExcerptSelectionRequest(
    val systemPrompt: String,
    val userContent: String,
    val generation: GenerationSettings,
)

data class RawExcerptCandidate(
    val startUnit: Int,
    val endUnit: Int,
    val independence: Int,
    val completeness: Int,
    val quotability: Int,
    val contextualFidelity: Int,
    val reason: String,
)

sealed interface InferenceOutcome {
    data class Success(val candidates: List<RawExcerptCandidate>, val modelId: String?) : InferenceOutcome
    data class ConnectionFailure(val message: String) : InferenceOutcome
    data class MalformedResponse(val message: String) : InferenceOutcome
}
