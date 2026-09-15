package no.esotericgames.quotes.server

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class Quote(val id: Int, val text: String, val author: String)

val sampleQuotes = listOf(
    Quote(1, "The best way to predict the future is to invent it.", "Alan Kay"),
    Quote(2, "Simplicity is the soul of efficiency.", "Austin Freeman"),
    Quote(
        3,
        "Programs must be written for people to read, and only incidentally for machines to execute.",
        "Harold Abelson",
    ),
)

@Serializable
data class WikiquoteImportRequest(
    val authorNames: List<String>,
    val sourceConfidence: Set<String> = setOf("sourced", "attributed", "unsourced", "disputed"),
)

@Serializable
data class WikiquoteAuthorImportResult(
    val requestedName: String,
    val resolvedTitle: String?,
    val found: Boolean,
    val quotesInserted: Int,
    val quotesSkippedAsDuplicate: Int,
    val quotesSkippedUntranslatable: Int,
    val quotesBySection: Map<String, Int>,
)

@Serializable
data class WikiquoteImportResponse(val results: List<WikiquoteAuthorImportResult>)

@Serializable
data class WikiquoteAuthorSearchResponse(val results: List<String>)
