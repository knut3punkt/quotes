package no.esotericgames.quotes.server.extraction

import no.esotericgames.quotes.server.extraction.llm.RawExcerptCandidate

/**
 * A selector candidate that passed deterministic structural validation, with its text reconstructed
 * from the original source. Not yet judged — carries no quality scores.
 */
data class ExcerptCandidate(
    val startUnit: Int,
    val endUnit: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val wordCount: Int,
    val reason: String,
    val contextSignals: List<String>,
)

/** A judged candidate, ready to persist. [contextualFidelity] is null when the fidelity check was skipped. */
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
    val contextualFidelity: Int?,
    val reason: String,
    val contextSignals: List<String>,
    val judgeNotes: String,
    val meetsThresholds: Boolean,
)

private val WHITESPACE_REGEX = Regex("""\s+""")
private const val MAX_REASON_LENGTH = 400

/**
 * Deterministic, LLM-independent validation and reconstruction — the core guarantee from
 * docs/features/quote-extraction.md that an accepted excerpt is genuinely present in the source.
 * Never trusts a structurally valid response by itself: every candidate is checked independently so
 * one malformed candidate can never affect its siblings, and the final text always comes from
 * `source.substring(...)` against the original offsets, never from LLM-generated text.
 *
 * Validation runs in two stages around the judge pass: [validate] before it (structure and exact
 * duplicates only — overlapping range variants are deliberately kept so the judge can choose
 * between them), and [resolveOverlaps] after it, ranked by the judge's verdict.
 */
object ExtractionValidator {

    fun validate(
        source: String,
        units: List<SourceUnit>,
        candidates: List<RawExcerptCandidate>,
        policy: ExtractionPolicy,
    ): List<ExcerptCandidate> {
        val unitsById = units.associateBy { it.id }
        return candidates
            .mapNotNull { toCandidateOrNull(source, it, unitsById, policy) }
            .distinctBy { it.startUnit to it.endUnit }
    }

    private fun toCandidateOrNull(
        source: String,
        candidate: RawExcerptCandidate,
        unitsById: Map<Int, SourceUnit>,
        policy: ExtractionPolicy,
    ): ExcerptCandidate? {
        val startUnit = unitsById[candidate.startUnit] ?: return null
        val endUnit = unitsById[candidate.endUnit] ?: return null
        if (candidate.startUnit > candidate.endUnit) return null

        val text = source.substring(startUnit.startOffset, endUnit.endOffset)
        val wordCount = text.trim().split(WHITESPACE_REGEX).count { it.isNotEmpty() }
        if (wordCount !in policy.acceptableMinWords..policy.acceptableMaxWords) return null

        val reason = listOfNotNull(
            candidate.coreIdea.trim().takeIf { it.isNotEmpty() }?.let { "Core idea: $it" },
            candidate.reason.trim().takeIf { it.isNotEmpty() },
        ).joinToString(" — ")

        return ExcerptCandidate(
            startUnit = candidate.startUnit,
            endUnit = candidate.endUnit,
            startOffset = startUnit.startOffset,
            endOffset = endUnit.endOffset,
            text = text,
            wordCount = wordCount,
            reason = reason.take(MAX_REASON_LENGTH),
            contextSignals = ContextDependencySignals.detect(text, candidate.startUnit, candidate.endUnit, candidate.references),
        )
    }

    /**
     * Ranks judged excerpts — passing ones first, then by summed score (a skipped fidelity check
     * counts as 0), then shorter range, then lower `startUnit` — and greedily accepts each one that
     * doesn't overlap an already-accepted range. A rejected range variant therefore never displaces
     * an accepted one. No count cap is applied — every non-overlapping excerpt is kept.
     */
    fun resolveOverlaps(excerpts: List<ValidatedExcerpt>): List<ValidatedExcerpt> {
        val ranked = excerpts.sortedWith(
            compareByDescending<ValidatedExcerpt> { it.meetsThresholds }
                .thenByDescending { it.independence + it.completeness + it.quotability + (it.contextualFidelity ?: 0) }
                .thenBy { it.endUnit - it.startUnit }
                .thenBy { it.startUnit },
        )
        val accepted = mutableListOf<ValidatedExcerpt>()
        for (excerpt in ranked) {
            val overlaps = accepted.any { excerpt.startUnit <= it.endUnit && it.startUnit <= excerpt.endUnit }
            if (!overlaps) accepted += excerpt
        }
        return accepted.sortedBy { it.startUnit }
    }
}
