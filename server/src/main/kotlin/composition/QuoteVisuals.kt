package no.esotericgames.quotes.server.composition

import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.imagegen.BackgroundLayout
import no.esotericgames.quotes.server.imagegen.DominantColor
import no.esotericgames.quotes.server.imagegen.NormalizedRect

/**
 * The images chosen for one showing of a quote (docs/features/quote-composition.md): a background and one
 * element for each motif tag, most relevant first, which the TV shows as a row of icons. The TV app lays them
 * out and picks colours from the metadata carried here. A null [background] means the quote has no mood
 * image, and the TV renders its fallback.
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
    /** The style profile it was generated in; null for images from recipes without style profiles. */
    val style: String?,
    /** The quote layout its prompt reserved room for (`left`, `top`, `overlay`, ...); null when there was none. */
    val quoteLayout: String?,
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
    /** The style profile it was generated in; null for images from recipes without style profiles. */
    val style: String?,
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

/**
 * A visual tag image reaching a quote through the whole quotation ([excerptId] null) or through one excerpt,
 * with the [relevance] of that tag assignment.
 */
data class CandidateRow(val quoteId: Int, val excerptId: Int?, val image: CandidateImage, val relevance: Int)

/**
 * Groups candidate rows into candidates per quote. [shownExcerpts] maps each quote to the excerpt being
 * shown, or to null when the full quotation is shown. A shown excerpt keeps its own tags and the whole
 * quotation's, not those of the quote's other excerpts. A tag can reach a quote through the whole quotation
 * and through several excerpts, so an image may appear more than once; it is kept once. Elements come in order
 * of their tag's highest relevance, then by tag name, so the most central motif is first.
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
        .groupBy { it.quoteId }
        .mapValues { (_, quoteRows) ->
            val unique = quoteRows.map { it.image }.distinctBy { it.imageId }
            val tagRelevance = quoteRows.groupBy { it.image.tagId }.mapValues { (_, tagRows) -> tagRows.maxOf { it.relevance } }
            QuoteVisualCandidates(
                backgrounds = unique.filterIsInstance<SelectedBackground>(),
                elements = unique.filterIsInstance<SelectedElement>().sortedWith(
                    compareByDescending<SelectedElement> { tagRelevance.getValue(it.tagId) }.thenBy { it.tagName },
                ),
            )
        }
