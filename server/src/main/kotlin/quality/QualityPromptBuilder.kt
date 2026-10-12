package no.esotericgames.quotes.server.quality

/**
 * The user turn for one subject: the text exactly as the TV would display it, plus author/source
 * metadata when known. An excerpt is assessed without its parent quote, because that is how it is shown.
 */
object QualityPromptBuilder {
    fun buildUserContent(text: String, authorName: String?, sourceTitle: String?): String {
        val metadataLines = buildList {
            authorName?.let { add("Author: $it") }
            sourceTitle?.let { add("Source: $it") }
        }
        return buildString {
            append("QUOTATION\n\n")
            append(text)
            if (metadataLines.isNotEmpty()) {
                append("\n\nMETADATA\n\n")
                append(metadataLines.joinToString("\n"))
            }
        }
    }
}
