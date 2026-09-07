package no.esotericgames.quotes.server.importing

import kotlinx.serialization.Serializable

@Serializable
data class ScriptureImportResult(
    val sourceId: Int,
    val quotesInserted: Int,
    val quotesSkippedAsDuplicate: Int,
)
