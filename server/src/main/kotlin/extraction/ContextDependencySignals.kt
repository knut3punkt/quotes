package no.esotericgames.quotes.server.extraction

import no.esotericgames.quotes.server.extraction.llm.UnitReference

// Words that, as the first word of an excerpt that doesn't start the source, usually continue an
// argument or point back at something the excerpt no longer contains.
private val BACKWARD_OPENERS = setOf(
    "but", "and", "so", "yet", "thus", "hence", "therefore", "however", "nor", "or", "then", "also",
    "indeed", "instead", "still", "accordingly", "consequently", "moreover", "furthermore", "besides",
    "this", "that", "these", "those", "such", "it", "its", "they", "their", "them", "he", "she", "his", "her",
    "him", "here", "there",
)

private val BACKWARD_PHRASES = listOf(
    "the former", "the latter", "the same", "as above", "as mentioned", "as i said", "as we have seen",
    "the above", "aforesaid", "aforementioned",
)

private val LEADING_WORD_REGEX = Regex("""^[\s"'“‘(\[—-]*([\p{L}']+)""")

/**
 * Cheap, deterministic hints that an excerpt may depend on context it no longer contains — the
 * "unresolved pronouns / transitional openers / 'as described above'" failure modes from
 * docs/features/quote-extraction.md's "Evaluate semantic independence" section.
 *
 * These are hints, not verdicts: the doc is explicit that a pronoun is not automatically
 * disqualifying, so nothing here rejects a candidate. The flagged phrases are passed to the blind
 * judge as targeted questions ("can a reader tell what 'this' refers to?") and stored for review.
 */
object ContextDependencySignals {

    fun detect(
        excerptText: String,
        startUnit: Int,
        endUnit: Int,
        selectorReferences: List<UnitReference>,
    ): List<String> {
        val signals = mutableListOf<String>()

        if (startUnit > 1) {
            val leadingWord = LEADING_WORD_REGEX.find(excerptText)?.groupValues?.get(1)
            if (leadingWord != null && leadingWord.lowercase() in BACKWARD_OPENERS) signals += leadingWord
        }

        val lowerText = excerptText.lowercase()
        BACKWARD_PHRASES.filter { it in lowerText }.forEach { signals += it }

        // The selector's own claim that a phrase's referent sits outside the chosen range.
        selectorReferences
            .filter { it.referentUnit !in startUnit..endUnit && it.phrase.isNotBlank() }
            .forEach { signals += it.phrase.trim() }

        return signals.distinctBy { it.lowercase() }
    }
}
