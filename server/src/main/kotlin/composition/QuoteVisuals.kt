package no.esotericgames.quotes.server.composition

import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.imagegen.BackgroundLayout
import no.esotericgames.quotes.server.imagegen.DominantColor
import no.esotericgames.quotes.server.imagegen.NormalizedRect

/**
 * The images chosen for one showing of a quote (docs/features/quote-composition.md). The TV app lays
 * them out and picks text colours from the metadata carried here. A null [background] means the quote
 * has no mood image, and the TV renders its fallback.
 */
@Serializable
data class QuoteVisuals(
    val background: SelectedBackground?,
    val elements: List<SelectedElement>,
)

/** A tag image that can be chosen for a quote; [tagId] groups the images of one tag. */
sealed interface CandidateImage {
    val imageId: Int
    val tagId: Int
}

@Serializable
data class SelectedBackground(
    override val imageId: Int,
    override val tagId: Int,
    val tagName: String,
    val width: Int,
    val height: Int,
    val meanLuminance: Double,
    val dominantColors: List<DominantColor>,
    val layout: BackgroundLayout,
) : CandidateImage

@Serializable
data class SelectedElement(
    override val imageId: Int,
    override val tagId: Int,
    val tagName: String,
    val width: Int,
    val height: Int,
    val dominantColors: List<DominantColor>,
    val contentBox: NormalizedRect,
) : CandidateImage

/** Every image a quote's visual tags offer, each image listed once. */
data class QuoteVisualCandidates(
    val backgrounds: List<SelectedBackground>,
    val elements: List<SelectedElement>,
) {
    companion object {
        val EMPTY = QuoteVisualCandidates(emptyList(), emptyList())
    }
}

/** A visual tag image reaching a quote through the whole quotation ([excerptId] null) or through one excerpt. */
data class CandidateRow(val quoteId: Int, val excerptId: Int?, val image: CandidateImage)

/**
 * Groups candidate rows into candidates per quote. [shownExcerpts] maps each quote to the excerpt being
 * shown, or to null when the full quotation is shown. A shown excerpt keeps its own tags and the whole
 * quotation's, not those of the quote's other excerpts. A tag can reach a quote through the whole quotation
 * and through several excerpts, so an image may appear more than once; it is kept once.
 */
fun groupVisualCandidates(
    rows: List<CandidateRow>,
    shownExcerpts: Map<Int, Int?>,
): Map<Int, QuoteVisualCandidates> =
    rows
        .filter { row ->
            val shownExcerptId = shownExcerpts[row.quoteId]
            row.excerptId == null || shownExcerptId == null || row.excerptId == shownExcerptId
        }
        .groupBy({ it.quoteId }, { it.image })
        .mapValues { (_, images) ->
            val unique = images.distinctBy { it.imageId }
            QuoteVisualCandidates(
                backgrounds = unique.filterIsInstance<SelectedBackground>(),
                elements = unique.filterIsInstance<SelectedElement>(),
            )
        }
