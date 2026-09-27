package no.esotericgames.quotes.server.interpretation

data class QuoteInterpretationContext(
    val text: String,
    val authorName: String? = null,
    val sourceTitle: String? = null,
    val sourceTypeCode: String? = null,
    // Set only when `text` is an excerpt rather than a whole quote: the parent quote's full original
    // text, supplied per docs/features/quote-interpretations.md's "Extracted quotations" section so
    // the model can avoid readings the surrounding passage would contradict.
    val originalContext: String? = null,
)

/**
 * Builds the user-turn content sent to the model, per docs/features/quote-interpretations.md's
 * "Context supplied to the model" and "Extracted quotations" sections: the quote (or excerpt) text
 * always, an optional original-context block when interpreting an excerpt, and — only when
 * resolvable — a metadata block, all clearly separated from the quotation text itself so the model
 * never confuses author/source metadata or surrounding context with the text under interpretation.
 */
object InterpretationPromptBuilder {
    fun buildUserContent(quote: QuoteInterpretationContext): String {
        val metadataLines = buildList {
            quote.authorName?.let { add("Author: $it") }
            quote.sourceTitle?.let { add("Source: $it") }
            quote.sourceTypeCode?.let { add("Source type: $it") }
        }

        return buildString {
            if (quote.originalContext != null) {
                append("QUOTE TO INTERPRET:\n\n")
                append(quote.text)
                append("\n\nORIGINAL CONTEXT:\n\n")
                append(quote.originalContext)
            } else {
                append("QUOTE:\n\n")
                append(quote.text)
            }
            if (metadataLines.isNotEmpty()) {
                append("\n\nMETADATA:\n\n")
                append(metadataLines.joinToString("\n"))
            }
        }
    }
}
