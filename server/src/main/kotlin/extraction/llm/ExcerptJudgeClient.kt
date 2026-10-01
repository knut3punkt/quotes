package no.esotericgames.quotes.server.extraction.llm

/**
 * Runtime-agnostic boundary for the judge pass (docs/features/quote-extraction.md's "Judge pass"),
 * mirroring [ExcerptSelectionClient]. Two separate calls, because they need different inputs:
 * [judgeStandalone] must never see the source (that blindness is the point — a model that has read
 * the surrounding passage cannot tell whether "this" is resolved for a reader who hasn't), while
 * [judgeFidelity] needs the source to compare meanings.
 */
interface ExcerptJudgeClient {
    suspend fun judgeStandalone(request: JudgeRequest): JudgeOutcome<StandaloneVerdict>
    suspend fun judgeFidelity(request: JudgeRequest): JudgeOutcome<FidelityVerdict>
}

data class JudgeRequest(
    val systemPrompt: String,
    val userContent: String,
    val generation: GenerationSettings,
)

/** The blind judge's reading of an excerpt shown without its source. Levels are 1-5, unvalidated. */
data class StandaloneVerdict(
    val whatItIsAbout: String,
    val unresolvedReferences: List<String>,
    val insight: String,
    val standsAlone: Int,
    val completeness: Int,
    val quotability: Int,
)

/** The source-aware judge's comparison of the excerpt with its full passage. Level is 1-5, unvalidated. */
data class FidelityVerdict(
    val meaningInSource: String,
    val fidelity: Int,
    val reason: String,
)

sealed interface JudgeOutcome<out T> {
    data class Success<T>(val verdict: T, val modelId: String?) : JudgeOutcome<T>
    data class ConnectionFailure(val message: String) : JudgeOutcome<Nothing>
    data class MalformedResponse(val message: String) : JudgeOutcome<Nothing>
}
