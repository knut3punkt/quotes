package no.esotericgames.quotes

data class Quote(val id: Int, val text: String, val author: String)

val sampleQuote = Quote(
    id = 1,
    text = "The best way to predict the future is to invent it.",
    author = "Alan Kay",
)

/** Renders the on-screen attribution line for a quote's author, e.g. "— Alan Kay". */
fun formatAttribution(author: String): String = "— $author"
