package no.esotericgames.quotes.server.imagegen

import no.esotericgames.quotes.server.tagging.TagFacet

/**
 * What a generated tag image is for (docs/features/tag-images.md). Each kind is generated for exactly
 * one tag facet and has its own prompt recipe and ComfyUI workflow.
 */
enum class ImageKind(val dbValue: String, val facet: TagFacet) {
    /** A full-screen background setting the atmosphere of a mood tag. */
    BACKGROUND("background", TagFacet.MOOD),

    /** A transparent collage element depicting a motif tag, layered over a background. */
    ELEMENT("element", TagFacet.MOTIF),
    ;

    companion object {
        fun forFacet(facet: TagFacet): ImageKind? = entries.firstOrNull { it.facet == facet }
    }
}
