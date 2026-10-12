package no.esotericgames.quotes.server.quality

import no.esotericgames.quotes.server.quality.llm.QualityResponseDto

private const val MAX_TEXT_LENGTH = 400
private const val MAX_REFERENCES = 10
private const val MAX_REFERENCE_LENGTH = 80
private val LEVEL_RANGE = 1..5

/** A model or uploaded assessment whose values have all been checked. */
data class ValidatedQualityAssessment(
    val reading: String,
    val unresolvedReferences: List<String>,
    val insight: String,
    val problems: List<QualityProblem>,
    val standsAlone: Int,
    val completeness: Int,
    val substance: Int,
    val expression: Int,
    val assessment: String,
    val verdict: QualityVerdict,
)

sealed interface QualityValidation {
    data class Valid(val assessment: ValidatedQualityAssessment) : QualityValidation
    data class Invalid(val reasons: List<String>) : QualityValidation
}

/**
 * Checks one structured assessment, whether it came from the local model or was uploaded by an
 * external runner — both go through this same function. Out-of-range levels and unknown verdicts or
 * problems are rejected rather than clamped or dropped, so a stored run holds only what the runner
 * actually answered and runs stay comparable.
 */
object QualityValidator {

    fun validate(response: QualityResponseDto): QualityValidation {
        val reasons = mutableListOf<String>()
        val levels = mapOf(
            "standsAlone" to response.standsAlone,
            "completeness" to response.completeness,
            "substance" to response.substance,
            "expression" to response.expression,
        )
        levels.filterValues { it !in LEVEL_RANGE }.forEach { (name, value) -> reasons += "$name $value is outside 1-5" }

        val verdict = QualityVerdict.fromDb(response.verdict.trim().lowercase())
        if (verdict == null) reasons += "unknown verdict \"${response.verdict}\""

        val problems = response.problems.map { it.trim().uppercase() }.distinct().mapNotNull { name ->
            QualityProblem.entries.firstOrNull { it.name == name } ?: run {
                reasons += "unknown problem \"$name\""
                null
            }
        }

        if (reasons.isNotEmpty() || verdict == null) return QualityValidation.Invalid(reasons)

        return QualityValidation.Valid(
            ValidatedQualityAssessment(
                reading = response.reading.trim().take(MAX_TEXT_LENGTH),
                unresolvedReferences = response.unresolvedReferences.map { it.trim().take(MAX_REFERENCE_LENGTH) }
                    .filter { it.isNotEmpty() }
                    .take(MAX_REFERENCES),
                insight = response.insight.trim().take(MAX_TEXT_LENGTH),
                problems = problems,
                standsAlone = response.standsAlone,
                completeness = response.completeness,
                substance = response.substance,
                expression = response.expression,
                assessment = response.assessment.trim().take(MAX_TEXT_LENGTH),
                verdict = verdict,
            ),
        )
    }
}
