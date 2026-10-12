package no.esotericgames.quotes.server.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CreateQualityRunRequest(
    val label: String,
    val runner: String, // "llm" | "external"
    val modelId: String? = null,
    val notes: String? = null,
)

@Serializable
data class QualityRunResponse(
    val id: Int,
    val label: String,
    val runner: String,
    val methodVersion: String,
    val promptVersion: String,
    val modelId: String?,
    val notes: String?,
    val createdAt: String,
    val assessedCount: Long,
    val failedCount: Long,
    /** Succeeded assessments per verdict. */
    val verdictCounts: Map<String, Long>,
)

@Serializable
data class AssessQualityRequest(val quoteIds: List<Int>)

@Serializable
data class QualityAssessmentResponse(
    val id: Int,
    val runId: Int,
    val quoteId: Int?,
    val excerptId: Int?,
    val subjectKind: String, // "quote" | "excerpt"
    val subjectText: String,
    val status: String, // "succeeded" | "failed"
    val errorMessage: String? = null,
    val reading: String? = null,
    val insight: String? = null,
    val assessment: String? = null,
    val unresolvedReferences: List<String> = emptyList(),
    val problems: List<String> = emptyList(),
    val standsAlone: Int? = null,
    val completeness: Int? = null,
    val substance: Int? = null,
    val expression: Int? = null,
    val verdict: String? = null,
    val precheckFlags: List<String> = emptyList(),
)

@Serializable
data class QuoteQualityResult(
    val quoteId: Int,
    val outcome: String, // "assessed" | "failed" | "notFound"
    val assessments: List<QualityAssessmentResponse>,
)

@Serializable
data class AssessQualityResponse(val results: List<QuoteQualityResult>)

/** One subject for an external runner, with the exact user message the local model would receive. */
@Serializable
data class QualitySubjectResponse(
    val quoteId: Int,
    val excerptId: Int? = null,
    val subjectKind: String,
    val textHash: String,
    val userContent: String,
)

@Serializable
data class UploadAssessmentItem(
    val quoteId: Int,
    val excerptId: Int? = null,
    val textHash: String,
    /** The runner's answer, in the shape of the response schema; validated like a model response. */
    val response: JsonObject,
)

@Serializable
data class UploadAssessmentsRequest(val items: List<UploadAssessmentItem>)

@Serializable
data class RejectedUploadItem(val quoteId: Int, val excerptId: Int? = null, val reason: String)

@Serializable
data class UploadAssessmentsResponse(val stored: Int, val rejected: List<RejectedUploadItem>)

@Serializable
data class QualityPromptResponse(
    val promptVersion: String,
    val methodVersion: String,
    val systemPrompt: String,
    val responseSchema: JsonObject,
)

@Serializable
data class LevelComparison(
    val name: String,
    val meanA: Double,
    val meanB: Double,
    val exactAgreement: Double,
)

@Serializable
data class FlagComparison(val flag: String, val onlyA: Int, val onlyB: Int, val both: Int)

@Serializable
data class QualityDisagreement(
    val subjectKind: String,
    val subjectText: String,
    val quoteId: Int?,
    val excerptId: Int?,
    val a: QualityAssessmentResponse,
    val b: QualityAssessmentResponse,
)

@Serializable
data class QualityComparisonResponse(
    val runA: Int,
    val runB: Int,
    /** Subjects both runs assessed successfully. */
    val matched: Int,
    val onlyInA: Int,
    val onlyInB: Int,
    /** Verdicts best first: excellent, good, acceptable, weak, reject. */
    val verdicts: List<String>,
    /** matrix[i][j]: subjects run A rated verdicts[i] and run B rated verdicts[j]. */
    val verdictMatrix: List<List<Int>>,
    val exactAgreement: Double,
    val withinOneAgreement: Double,
    val weightedKappa: Double,
    /** keep = excellent/good/acceptable, drop = weak/reject: [[keepKeep, keepDrop], [dropKeep, dropDrop]]. */
    val keepDropMatrix: List<List<Int>>,
    val keepDropKappa: Double,
    val levels: List<LevelComparison>,
    val flags: List<FlagComparison>,
    val disagreements: List<QualityDisagreement>,
)
