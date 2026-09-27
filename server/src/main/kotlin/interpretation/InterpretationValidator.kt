package no.esotericgames.quotes.server.interpretation

import no.esotericgames.quotes.server.interpretation.llm.MAX_INTERPRETATIONS
import no.esotericgames.quotes.server.interpretation.llm.MAX_INTERPRETATION_LENGTH
import no.esotericgames.quotes.server.interpretation.llm.MAX_LENS_LENGTH
import no.esotericgames.quotes.server.interpretation.llm.RawInterpretationCandidate

data class ValidatedInterpretation(
    val lens: String,
    val interpretation: String,
    val textualSupport: Int,
    val speculativeness: Int,
)

private val WHITESPACE_REGEX = Regex("""\s+""")

/**
 * Deterministic, LLM-independent validation, per docs/features/quote-interpretations.md's
 * "Application-side validation" section. Never trusts a structurally valid response by itself: every
 * candidate is checked independently so one malformed candidate can never affect its siblings.
 *
 * Unlike [no.esotericgames.quotes.server.extraction.ExtractionValidator], there is no text
 * reconstruction step — interpretation text is generated prose, not a substring of a source, so it is
 * trusted directly once it passes these checks. There is also no overlap-resolution step (no offsets
 * exist to overlap); only exact-duplicate normalized-text detection applies.
 */
object InterpretationValidator {

    fun validate(candidates: List<RawInterpretationCandidate>): List<ValidatedInterpretation> {
        val structurallyValid = candidates.mapNotNull(::toValidatedOrNull)
        val deduplicated = dropDuplicates(structurallyValid)
        return deduplicated.take(MAX_INTERPRETATIONS)
    }

    private fun toValidatedOrNull(candidate: RawInterpretationCandidate): ValidatedInterpretation? {
        val lens = candidate.lens.trim()
        val interpretation = candidate.interpretation.trim()
        if (lens.isEmpty() || lens.length > MAX_LENS_LENGTH) return null
        if (interpretation.isEmpty() || interpretation.length > MAX_INTERPRETATION_LENGTH) return null

        return ValidatedInterpretation(
            lens = lens,
            interpretation = interpretation,
            textualSupport = candidate.textualSupport.coerceIn(0, 100),
            speculativeness = candidate.speculativeness.coerceIn(0, 100),
        )
    }

    private fun normalize(text: String): String = text.lowercase().replace(WHITESPACE_REGEX, " ").trim()

    /** Keeps the first occurrence of each exact normalized interpretation text within one response. */
    private fun dropDuplicates(candidates: List<ValidatedInterpretation>): List<ValidatedInterpretation> {
        val seen = mutableSetOf<String>()
        return candidates.filter { seen.add(normalize(it.interpretation)) }
    }
}
