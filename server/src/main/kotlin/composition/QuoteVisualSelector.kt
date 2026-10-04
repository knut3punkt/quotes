package no.esotericgames.quotes.server.composition

import kotlin.random.Random

/** The most collage elements one quote screen gets; the TV layouts cover 0 to this many. */
const val MAX_COLLAGE_ELEMENTS = 4

/**
 * Chooses the background and collage elements for one showing of a quote
 * (docs/features/quote-composition.md, "Selection"):
 *
 * 1. a random mood tag among those with images, then a random image of it, or none;
 * 2. a random target N in 1..[MAX_COLLAGE_ELEMENTS], then up to N distinct motif tags with images,
 *    one random image each.
 */
class QuoteVisualSelector(private val random: Random = Random.Default) {

    fun select(candidates: QuoteVisualCandidates): QuoteVisuals {
        val background = candidates.backgrounds.groupBy { it.tagId }.values.randomOrNull(random)?.random(random)
        val targetCount = random.nextInt(1, MAX_COLLAGE_ELEMENTS + 1)
        val elements = candidates.elements.groupBy { it.tagId }.values
            .shuffled(random)
            .take(targetCount)
            .map { it.random(random) }
        return QuoteVisuals(background, elements)
    }
}
