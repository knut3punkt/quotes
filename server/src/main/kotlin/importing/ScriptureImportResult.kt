package no.esotericgames.quotes.server.importing

import kotlinx.serialization.Serializable

@Serializable
data class ScriptureImportResult(
    val sourceId: Int,
    val quotesInserted: Int,
    val quotesSkippedAsDuplicate: Int,
    // Only ever non-zero for seed-list importers (Bible, Quran): a seed reference the source API
    // couldn't resolve, or that came back empty. Whole-canon importers (Tao Te Ching, Gita,
    // Dhammapada) have nothing that can fail to resolve, since they enumerate the canon itself.
    val quotesFailedToFetch: Int = 0,
)
