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
