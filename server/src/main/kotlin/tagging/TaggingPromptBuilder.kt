package no.esotericgames.quotes.server.tagging

data class InterpretationContext(
    val lens: String,
    val interpretation: String,
    val textualSupport: Int,
    val speculativeness: Int,
)

data class QuoteTaggingContext(
    val text: String,
    val authorName: String? = null,
    val sourceTitle: String? = null,
    // Set only when `text` is an excerpt: the parent quote's full text, so the model avoids tags the
    // surrounding passage would contradict.
    val originalContext: String? = null,
    val interpretations: List<InterpretationContext> = emptyList(),
)

/** The existing canonical tag names per facet, most used first, that the model should prefer. */
data class VocabularyHint(
    val concepts: List<String>,
    val moods: List<String>,
    val motifs: List<String>,
) {
    companion object {
        val EMPTY = VocabularyHint(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Builds the user-turn content for one tagging call. Every block is clearly labelled so the model never
 * confuses metadata, surrounding context, interpretations or the existing vocabulary with the text being
 * tagged. The author and source are given for disambiguation only; the prompt tells the model not to
 * tag them.
 */
object TaggingPromptBuilder {
    fun buildUserContent(quote: QuoteTaggingContext, vocabulary: VocabularyHint): String = buildString {
        if (quote.originalContext != null) {
            append("QUOTE TO TAG:\n\n")
            append(quote.text)
            append("\n\nORIGINAL CONTEXT:\n\n")
            append(quote.originalContext)
        } else {
            append("QUOTE:\n\n")
            append(quote.text)
        }

        val metadataLines = buildList {
            quote.authorName?.let { add("Author: $it") }
            quote.sourceTitle?.let { add("Source: $it") }
        }
        if (metadataLines.isNotEmpty()) {
            append("\n\nMETADATA:\n\n")
            append(metadataLines.joinToString("\n"))
        }

        if (quote.interpretations.isNotEmpty()) {
            append("\n\nINTERPRETATIONS:\n\n")
            val readings = quote.interpretations.mapIndexed { index, reading ->
                "${index + 1}. [${reading.lens}; textual support ${reading.textualSupport}/100, " +
                    "speculativeness ${reading.speculativeness}/100] ${reading.interpretation}"
            }
            append(readings.joinToString("\n"))
        }

        append("\n\nEXISTING TAGS (prefer one of these whenever it means the same thing):\n\n")
        append("Concepts: ${vocabularyLine(vocabulary.concepts)}\n")
        append("Moods: ${vocabularyLine(vocabulary.moods)}\n")
        append("Motifs: ${vocabularyLine(vocabulary.motifs)}")
    }

    private fun vocabularyLine(names: List<String>): String = if (names.isEmpty()) "(none yet)" else names.joinToString(", ")
}
