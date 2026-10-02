package no.esotericgames.quotes.server.tagging

data class NormalizedTagName(val displayName: String, val normalizedName: String)

private val DISALLOWED_CHARACTERS = Regex("""[^\p{L}\p{N}\s'\-]""")
private val WHITESPACE = Regex("""\s+""")
private val ARTICLES = setOf("the", "a", "an")
private val LEADING_ARTICLE = Regex("""^(the|a|an)\s+""")
private const val EDGE_PUNCTUATION = "'-"
private const val MIN_LENGTH_FOR_PLURAL_FOLD = 4

/**
 * Deterministic tag-name normalization, the first of the open vocabulary's defences against
 * fragmentation (docs/features/quote-tagging.md). Two names that normalize to the same string within a
 * facet are the same tag.
 *
 * Display names are lowercased too, so the vocabulary reads consistently. An admin can rename a tag to
 * fix casing, since the normalized form stays the same.
 *
 * Only motifs get a plural fold ("stars" -> "star"): motifs name drawable things, where singular and
 * plural are the same asset. Concepts do not, because "values" and "value", or "ethics" and "ethic",
 * are different ideas. The fold is a simple suffix rule and can be wrong ("lens" -> "len"); that is
 * harmless for matching, since both forms normalize alike, and an admin rename fixes the display name.
 */
object TagNormalization {

    fun normalize(rawName: String, facet: TagFacet): NormalizedTagName? {
        val cleaned = rawName
            .replace(DISALLOWED_CHARACTERS, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .trim { it in EDGE_PUNCTUATION || it.isWhitespace() }
            .lowercase()
            .replace(LEADING_ARTICLE, "")
        if (cleaned.isEmpty() || cleaned in ARTICLES) return null

        val folded = if (facet == TagFacet.MOTIF) foldPluralOfLastWord(cleaned) else cleaned
        return NormalizedTagName(displayName = folded, normalizedName = folded)
    }

    /** Normalizes an admin-chosen display name: the matching key is normalized, but the display text is kept. */
    fun normalizeKeepingDisplay(rawName: String, facet: TagFacet): NormalizedTagName? {
        val normalized = normalize(rawName, facet) ?: return null
        return normalized.copy(displayName = rawName.replace(WHITESPACE, " ").trim())
    }

    private fun foldPluralOfLastWord(phrase: String): String {
        val words = phrase.split(' ')
        return (words.dropLast(1) + singularize(words.last())).joinToString(" ")
    }

    private fun singularize(word: String): String = when {
        word.length < MIN_LENGTH_FOR_PLURAL_FOLD -> word
        word.endsWith("ies") -> word.dropLast(3) + "y"
        word.endsWith("sses") || word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") ->
            word.dropLast(2)
        word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
        word.endsWith("s") -> word.dropLast(1)
        else -> word
    }
}
