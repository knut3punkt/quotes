package no.esotericgames.quotes.server.extraction

/**
 * One ordered, deterministically-derived segment of a source quote. Offsets always point into the
 * original, unnormalized source string — never into a preprocessed/normalized copy — so an excerpt
 * can always be reconstructed verbatim from the source alone.
 */
data class SourceUnit(
    val id: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
)
