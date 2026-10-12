package no.esotericgames.quotes.server.composition

import kotlin.random.Random

/**
 * At most this many motif icons per showing. Tagging keeps at most three motifs per quotation or excerpt, but a
 * shown excerpt also carries the whole quotation's tags.
 */
const val MAX_MOTIF_ICONS = 4

/**
 * Chooses the images for a showing of a quote (docs/features/quote-composition.md, "Selection"):
 *
 * 1. a random mood tag among those with images, and a random background of it;
 * 2. one random image of every motif tag, in the candidates' order (most relevant first), up to
 *    [MAX_MOTIF_ICONS].
 *
 * Picking a tag before its image gives every tag the same chance, however many images it has.
 */
class QuoteVisualSelector(private val random: Random = Random.Default) {

    fun select(candidates: QuoteVisualCandidates): QuoteVisuals {
        val background = candidates.backgrounds.groupBy { it.tagId }.values.randomOrNull(random)?.random(random)
        val elements = candidates.elements.groupBy { it.tagId }.values
            .take(MAX_MOTIF_ICONS)
            .map { tagImages -> tagImages.random(random) }
        return QuoteVisuals(background = background, elements = elements)
    }
}
