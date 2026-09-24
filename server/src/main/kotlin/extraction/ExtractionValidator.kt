package no.esotericgames.quotes.server.extraction

import no.esotericgames.quotes.server.extraction.llm.RawExcerptCandidate

data class ValidatedExcerpt(
    val startUnit: Int,
    val endUnit: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val wordCount: Int,
    val independence: Int,
    val completeness: Int,
    val quotability: Int,
    val contextualFidelity: Int,
    val reason: String,
    val meetsThresholds: Boolean,
)

private val WHITESPACE_REGEX = Regex("""\s+""")
private const val MAX_REASON_LENGTH = 200

/**
 * Deterministic, LLM-independent validation and reconstruction — the core guarantee from
 * docs/features/quote-extraction.md that an accepted excerpt is genuinely present in the source.
 * Never trusts a structurally valid response by itself: every candidate is checked independently so
 * one malformed candidate can never affect its siblings, and the final text always comes from
 * `source.substring(...)` against the original offsets, never from LLM-generated text.
 */
object ExtractionValidator {

    fun validate(
        source: String,
        units: List<SourceUnit>,
        candidates: List<RawExcerptCandidate>,
        policy: ExtractionPolicy,
    ): List<ValidatedExcerpt> {
        val unitsById = units.associateBy { it.id }
        val structurallyValid = candidates.mapNotNull { toValidatedOrNull(source, it, unitsById, policy) }
        return resolveOverlaps(structurallyValid)
    }

    private fun toValidatedOrNull(
        source: String,
        candidate: RawExcerptCandidate,
        unitsById: Map<Int, SourceUnit>,
        policy: ExtractionPolicy,
    ): ValidatedExcerpt? {
        val startUnit = unitsById[candidate.startUnit] ?: return null
        val endUnit = unitsById[candidate.endUnit] ?: return null
        if (candidate.startUnit > candidate.endUnit) return null

        val text = source.substring(startUnit.startOffset, endUnit.endOffset)
        val wordCount = text.trim().split(WHITESPACE_REGEX).count { it.isNotEmpty() }
        if (wordCount !in policy.acceptableMinWords..policy.acceptableMaxWords) return null

        val independence = candidate.independence.coerceIn(0, 100)
        val completeness = candidate.completeness.coerceIn(0, 100)
        val quotability = candidate.quotability.coerceIn(0, 100)
        val contextualFidelity = candidate.contextualFidelity.coerceIn(0, 100)
        val meetsThresholds = independence >= policy.minIndependence &&
            completeness >= policy.minCompleteness &&
            contextualFidelity >= policy.minContextualFidelity &&
            quotability >= policy.minQuotability

        return ValidatedExcerpt(
            startUnit = candidate.startUnit,
            endUnit = candidate.endUnit,
            startOffset = startUnit.startOffset,
            endOffset = endUnit.endOffset,
            text = text,
            wordCount = wordCount,
            independence = independence,
            completeness = completeness,
            quotability = quotability,
            contextualFidelity = contextualFidelity,
            reason = candidate.reason.take(MAX_REASON_LENGTH),
            meetsThresholds = meetsThresholds,
        )
    }

    /**
     * Ranks candidates by summed score (highest first, so ties are unlikely and results are
     * reproducible), then shorter range, then lower `startUnit`, and greedily accepts each one that
     * doesn't overlap an already-accepted range. This covers exact duplicates (which trivially
     * overlap themselves) and partial overlaps in a single deterministic pass. No count cap is
     * applied — every non-overlapping validated candidate is kept.
     */
    private fun resolveOverlaps(candidates: List<ValidatedExcerpt>): List<ValidatedExcerpt> {
        val ranked = candidates.sortedWith(
            compareByDescending<ValidatedExcerpt> { it.independence + it.completeness + it.quotability + it.contextualFidelity }
                .thenBy { it.endUnit - it.startUnit }
                .thenBy { it.startUnit },
        )
        val accepted = mutableListOf<ValidatedExcerpt>()
        for (candidate in ranked) {
            val overlaps = accepted.any { candidate.startUnit <= it.endUnit && it.startUnit <= candidate.endUnit }
            if (!overlaps) accepted += candidate
        }
        return accepted.sortedBy { it.startUnit }
    }
}
