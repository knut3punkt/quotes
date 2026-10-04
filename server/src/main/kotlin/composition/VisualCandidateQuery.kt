package no.esotericgames.quotes.server.composition

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import no.esotericgames.quotes.server.db.QuoteTagAssignments
import no.esotericgames.quotes.server.db.TagImages
import no.esotericgames.quotes.server.db.Tags
import no.esotericgames.quotes.server.imagegen.BackgroundLayout
import no.esotericgames.quotes.server.imagegen.DominantColor
import no.esotericgames.quotes.server.imagegen.ElementLayout
import no.esotericgames.quotes.server.imagegen.ImageKind
import no.esotericgames.quotes.server.tagging.TagFacet
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory

/** quote-tagging.md: "Visuals should use only central and significant tags." */
private const val MIN_VISUAL_RELEVANCE = 2

private val layoutJson = Json { ignoreUnknownKeys = true }

private val logger = LoggerFactory.getLogger("no.esotericgames.quotes.server.composition.VisualCandidateQuery")

/**
 * The visual candidates of each given quote: images of its active, central or significant mood and motif
 * tags, whether the tag sits on the whole quotation or on one of its excerpts (an excerpt-tagged quotation
 * has no whole-quotation tags, and the TV shows the full text). Elements without transparency are left
 * out, since an opaque square breaks a collage. Quotes without candidates are absent from the map.
 * Must run inside a transaction.
 */
fun selectVisualCandidates(quoteIds: Collection<Int>): Map<Int, QuoteVisualCandidates> {
    if (quoteIds.isEmpty()) return emptyMap()
    val rows = QuoteTagAssignments.innerJoin(Tags).innerJoin(TagImages)
        .selectAll()
        .where {
            (QuoteTagAssignments.quoteId inList quoteIds) and
                (QuoteTagAssignments.rejected eq false) and
                (QuoteTagAssignments.relevance greaterEq MIN_VISUAL_RELEVANCE) and
                (
                    ((Tags.facet eq TagFacet.MOOD.dbValue) and (TagImages.kind eq ImageKind.BACKGROUND.dbValue)) or
                        (
                            (Tags.facet eq TagFacet.MOTIF.dbValue) and
                                (TagImages.kind eq ImageKind.ELEMENT.dbValue) and
                                (TagImages.hasAlpha eq true)
                            )
                    )
        }
        .mapNotNull { row -> row.toCandidateImage()?.let { row[QuoteTagAssignments.quoteId] to it } }
    return groupVisualCandidates(rows)
}

private fun ResultRow.toCandidateImage(): CandidateImage? {
    val imageId = this[TagImages.id]
    val dominantColors = runCatching {
        layoutJson.decodeFromJsonElement<List<DominantColor>>(this[TagImages.dominantColors])
    }.getOrElse {
        logger.warn("tag image {} has unreadable dominant_colors, skipping it", imageId, it)
        return null
    }
    return when (this[TagImages.kind]) {
        ImageKind.BACKGROUND.dbValue -> {
            val layout = runCatching { layoutJson.decodeFromJsonElement<BackgroundLayout>(this[TagImages.layout]) }
                .getOrElse {
                    logger.warn("background image {} has an unreadable layout, skipping it", imageId, it)
                    return null
                }
            SelectedBackground(
                imageId = imageId,
                tagId = this[Tags.id],
                tagName = this[Tags.name],
                width = this[TagImages.width],
                height = this[TagImages.height],
                meanLuminance = this[TagImages.meanLuminance].toDouble(),
                dominantColors = dominantColors,
                layout = layout,
            )
        }
        else -> {
            val contentBox = runCatching { layoutJson.decodeFromJsonElement<ElementLayout>(this[TagImages.layout]) }
                .getOrNull()?.contentBox
                // A fully transparent element has nothing to show.
                ?: return null
            SelectedElement(
                imageId = imageId,
                tagId = this[Tags.id],
                tagName = this[Tags.name],
                width = this[TagImages.width],
                height = this[TagImages.height],
                dominantColors = dominantColors,
                contentBox = contentBox,
            )
        }
    }
}
