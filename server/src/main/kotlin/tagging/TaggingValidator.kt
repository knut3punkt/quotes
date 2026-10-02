package no.esotericgames.quotes.server.tagging

import no.esotericgames.quotes.server.tagging.llm.MAX_TAG_NAME_LENGTH
import no.esotericgames.quotes.server.tagging.llm.RawTagCandidate
import no.esotericgames.quotes.server.tagging.llm.RawTagging

data class ValidatedTag(
    val facet: TagFacet,
    val name: NormalizedTagName,
    val breadth: TagBreadth?,
    val relevance: Int,
    val basis: TagBasis,
)

private val MAX_WORDS = mapOf(TagFacet.CONCEPT to 5, TagFacet.MOOD to 2, TagFacet.MOTIF to 3)

/**
 * Deterministic, LLM-independent validation of one tagging response. Each candidate is checked on its
 * own, so a malformed tag is dropped without affecting its siblings.
 *
 * Within a facet, candidates that normalize to the same name are merged: the highest relevance wins,
 * and `text` basis wins over `interpretation`, since a tag supported by the words themselves is the
 * stronger claim. Each facet is then capped at the policy limit, keeping the most relevant tags.
 */
object TaggingValidator {

    fun validate(raw: RawTagging, policy: TaggingPolicy): List<ValidatedTag> =
        validateFacet(TagFacet.CONCEPT, raw.concepts, policy.maxConcepts) +
            validateFacet(TagFacet.MOOD, raw.moods, policy.maxMoods) +
            validateFacet(TagFacet.MOTIF, raw.motifs, policy.maxMotifs)

    private fun validateFacet(facet: TagFacet, candidates: List<RawTagCandidate>, cap: Int): List<ValidatedTag> {
        val merged = LinkedHashMap<String, ValidatedTag>()
        candidates.mapNotNull { toValidatedOrNull(facet, it) }.forEach { tag ->
            val existing = merged[tag.name.normalizedName]
            merged[tag.name.normalizedName] = if (existing == null) tag else mergeDuplicates(existing, tag)
        }
        return merged.values.sortedByDescending { it.relevance }.take(cap)
    }

    private fun toValidatedOrNull(facet: TagFacet, candidate: RawTagCandidate): ValidatedTag? {
        val name = TagNormalization.normalize(candidate.name, facet) ?: return null
        if (name.displayName.length > MAX_TAG_NAME_LENGTH) return null
        if (name.displayName.split(' ').size > MAX_WORDS.getValue(facet)) return null
        val basis = TagBasis.fromDbValue(candidate.basis.trim().lowercase()) ?: return null
        val breadth = if (facet == TagFacet.CONCEPT) {
            TagBreadth.fromDbValue(candidate.breadth?.trim()?.lowercase().orEmpty()) ?: return null
        } else {
            null
        }

        return ValidatedTag(
            facet = facet,
            name = name,
            breadth = breadth,
            relevance = candidate.relevance.coerceIn(MIN_RELEVANCE, MAX_RELEVANCE),
            basis = basis,
        )
    }

    private fun mergeDuplicates(first: ValidatedTag, second: ValidatedTag): ValidatedTag = first.copy(
        relevance = maxOf(first.relevance, second.relevance),
        basis = if (first.basis == TagBasis.TEXT || second.basis == TagBasis.TEXT) TagBasis.TEXT else TagBasis.INTERPRETATION,
    )
}
