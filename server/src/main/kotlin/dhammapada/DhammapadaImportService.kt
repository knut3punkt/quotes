package no.esotericgames.quotes.server.dhammapada

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.esotericgames.quotes.server.importing.ScriptureImportResult
import no.esotericgames.quotes.server.importing.SourceDescriptor
import no.esotericgames.quotes.server.importing.StagedQuoteCandidate
import no.esotericgames.quotes.server.importing.findOrCreateSource
import no.esotericgames.quotes.server.importing.stageQuote

private const val PROVIDER = "dhammapada-sujato"
private const val TRANSLATOR = "sujato"

// The Dhammapada's 423 verses are traditionally grouped into 26 vaggas (chapters); SuttaCentral's
// range endpoint only accepts these canonical ranges, not arbitrary spans (verified: "dhp1-100"
// 404s, but "dhp1-20", the real first vagga, works).
private val VAGGA_RANGES = listOf(
    1 to 20, 21 to 32, 33 to 43, 44 to 59, 60 to 75, 76 to 89, 90 to 99, 100 to 115,
    116 to 128, 129 to 145, 146 to 156, 157 to 166, 167 to 178, 179 to 196, 197 to 208,
    209 to 220, 221 to 234, 235 to 255, 256 to 272, 273 to 289, 290 to 305, 306 to 319,
    320 to 333, 334 to 359, 360 to 382, 383 to 423,
)

private val SEGMENT_KEY = Regex("""^dhp(\d+):(\d+(?:\.\d+)?)$""")

/**
 * Imports the Dhammapada from SuttaCentral's bilara-data, via Bhikkhu Sujato's CC0 translation.
 * Each verse is split across several line-level segments (e.g. "dhp1:1".."dhp1:6"); this
 * concatenates them in order into one quote-length unit per verse.
 */
class DhammapadaImportService(private val client: DhammapadaClient) {

    suspend fun import(): ScriptureImportResult {
        val sourceId = findOrCreateSource(
            SourceDescriptor(
                title = "Dhammapada",
                typeCode = "scripture",
                citationUnit = "verse",
                license = "CC0",
                attributionText = "Trans. Bhikkhu Sujato",
                translation = "Sujato",
            ),
        )

        var inserted = 0
        var duplicates = 0
        for ((start, end) in VAGGA_RANGES) {
            val range = client.fetchRange(start, end, TRANSLATOR)
            val linesByVerse = range.translationText.entries
                .mapNotNull { (key, text) -> parseSegmentKey(key)?.let { (verse, line) -> Triple(verse, line, text) } }
                .filter { (_, _, text) -> text.isNotBlank() }
                .groupBy({ it.first }, { it.second to it.third })

            for (verseNumber in start..end) {
                val lines = linesByVerse[verseNumber] ?: continue
                val text = lines.sortedBy { it.first }.joinToString(" ") { it.second.trim() }
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (text.isEmpty()) continue

                val payload = buildJsonObject {
                    put("verse", verseNumber)
                    put("translator", TRANSLATOR)
                    put("sourceUrl", "https://suttacentral.net/dhp$verseNumber/en/$TRANSLATOR")
                }
                val result = stageQuote(
                    StagedQuoteCandidate(
                        provider = PROVIDER,
                        providerQuoteId = "dhp$verseNumber",
                        rawText = text,
                        rawAuthor = "Buddha",
                        rawSourceLocation = verseNumber.toString(),
                        sourceConfidence = "sourced",
                        rawPayload = payload,
                    ),
                )
                if (result.inserted) inserted++ else duplicates++
            }
        }

        return ScriptureImportResult(sourceId = sourceId, quotesInserted = inserted, quotesSkippedAsDuplicate = duplicates)
    }
}

/** "dhp1:1" -> verse 1, line 1. "dhp1:0.3" is a title/header segment (line 0.x), always skipped. */
internal fun parseSegmentKey(key: String): Pair<Int, Double>? {
    val match = SEGMENT_KEY.find(key) ?: return null
    val verse = match.groupValues[1].toInt()
    val line = match.groupValues[2].toDouble()
    if (line < 1) return null
    return verse to line
}
