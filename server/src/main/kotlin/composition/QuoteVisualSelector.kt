package no.esotericgames.quotes.server.composition

import kotlin.random.Random

/**
 * Chooses the background and the one motif element for a showing of a quote
 * (docs/features/quote-composition.md, "Selection"):
 *
 * 1. a random mood tag among those with images;
 * 2. among that tag's backgrounds and every motif image, the pairs whose styles fit best by [affinity], one
 *    picked at random. Every (background, motif tag) counts once, by its best-fitting image of the tag, so a
 *    tag with many images gets no extra chance. When nothing fits, the pick is among all pairs.
 *
 * Without backgrounds, the motif is a random tag's random image; without motifs, the background is a random
 * image of the mood tag.
 */
class QuoteVisualSelector(
    private val random: Random = Random.Default,
    private val affinity: StyleAffinity = StyleAffinity.BUNDLED,
) {

    fun select(candidates: QuoteVisualCandidates): QuoteVisuals {
        val motifTags = candidates.elements.groupBy { it.tagId }.values.toList()
        val moodImages = candidates.backgrounds.groupBy { it.tagId }.values.randomOrNull(random)
        if (moodImages == null) {
            val element = motifTags.randomOrNull(random)?.random(random)
            return QuoteVisuals(background = null, elements = listOfNotNull(element), styleFit = null)
        }
        if (motifTags.isEmpty()) {
            return QuoteVisuals(background = moodImages.random(random), elements = emptyList(), styleFit = null)
        }

        val pairs = moodImages.flatMap { background ->
            motifTags.map { tagImages -> bestPairWith(background, tagImages) }
        }
        val bestFit = pairs.maxOf { it.fit }
        val chosen = pairs.filter { it.fit == bestFit }.random(random)
        return QuoteVisuals(background = chosen.background, elements = listOf(chosen.element), styleFit = chosen.fit)
    }

    /** The best-fitting of one motif tag's images over [background]; ties go to chance. */
    private fun bestPairWith(background: SelectedBackground, tagImages: List<SelectedElement>): ScoredPair {
        val scored = tagImages.map { ScoredPair(background, it, affinity.score(background.style, it.style)) }
        val bestFit = scored.maxOf { it.fit }
        return scored.filter { it.fit == bestFit }.random(random)
    }

    private data class ScoredPair(val background: SelectedBackground, val element: SelectedElement, val fit: Int)
}
