package no.esotericgames.quotes.server.tagging.llm

import no.esotericgames.quotes.server.extraction.llm.GenerationSettings

/**
 * The boundary that keeps the local inference runtime replaceable, mirroring
 * [no.esotericgames.quotes.server.interpretation.llm.InterpretationClient]. Speaks only in plain prompt
 * text and a runtime-agnostic result.
 */
interface TaggingClient {
    suspend fun generateTags(request: TaggingRequest): InferenceOutcome
}

data class TaggingRequest(
    val systemPrompt: String,
    val userContent: String,
    val generation: GenerationSettings,
)

/** One tag as the model returned it: unvalidated, unnormalized strings. `breadth` is null for moods and motifs. */
data class RawTagCandidate(
    val name: String,
    val breadth: String?,
    val relevance: Int,
    val basis: String,
)

data class RawTagging(
    val concepts: List<RawTagCandidate>,
    val moods: List<RawTagCandidate>,
    val motifs: List<RawTagCandidate>,
)

sealed interface InferenceOutcome {
    data class Success(val tagging: RawTagging, val modelId: String?) : InferenceOutcome
    data class ConnectionFailure(val message: String) : InferenceOutcome
    data class MalformedResponse(val message: String) : InferenceOutcome
}
