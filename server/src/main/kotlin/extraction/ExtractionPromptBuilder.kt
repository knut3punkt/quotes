package no.esotericgames.quotes.server.extraction

/**
 * Builds the user-turn content that presents numbered source units to the model, per
 * docs/features/quote-extraction.md's "Model input" section — the model never sees raw source text
 * outside of these units, so it cannot reproduce text it wasn't given a unit for.
 */
object ExtractionPromptBuilder {
    fun buildUserContent(units: List<SourceUnit>): String {
        val unitLines = units.joinToString("\n") { "[${it.id}] ${it.text}" }
        return "SOURCE UNITS\n\n$unitLines\n\nSelect zero or more quotable standalone excerpts according to the extraction rules."
    }
}
