package no.esotericgames.quotes.server.quran

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.esotericgames.quotes.server.importing.ScriptureImportResult
import no.esotericgames.quotes.server.importing.SourceDescriptor
import no.esotericgames.quotes.server.importing.StagedQuoteCandidate
import no.esotericgames.quotes.server.importing.findOrCreateSource
import no.esotericgames.quotes.server.importing.loadBundledJsonResource
import no.esotericgames.quotes.server.importing.stageQuote

private const val EDITION = "en.pickthall"
private const val PROVIDER = "alquran-cloud-pickthall"
private const val RESOURCE_PATH = "/scripture/quran-pickthall-seed-refs.json"
private val REFERENCE = Regex("""^(\d+):(\d+)(?:-(\d+))?$""")

/**
 * Imports a small, hand-curated seed list of well-known Quran passages, translated by Mohammed
 * Marmaduke William Pickthall (1930 — normally treated as free-to-reuse; verify before any
 * commercial redistribution). Like the Bible, the Quran has 6,236 ayahs and no source of
 * pre-curated "quotable" ones, so a reviewable seed-list file is the deliberate substitute for
 * automated candidate scoring.
 */
class QuranImportService(private val client: QuranClient) {

    suspend fun import(): ScriptureImportResult {
        val references = loadBundledJsonResource<List<String>>(RESOURCE_PATH)
        val sourceId = findOrCreateSource(
            SourceDescriptor(
                title = "The Quran",
                typeCode = "scripture",
                citationUnit = "surah:ayah",
                license = "verify-before-commercial-use",
                attributionText = "Trans. Mohammed Marmaduke William Pickthall (1930)",
                translation = "Pickthall",
            ),
        )

        var inserted = 0
        var duplicates = 0
        var failed = 0
        for (reference in references) {
            val range = parseReference(reference)
            if (range == null) {
                failed++
                continue
            }
            val (surah, ayahStart, ayahEnd) = range

            val ayahs = (ayahStart..ayahEnd).mapNotNull { ayahNumber ->
                runCatching { client.fetchAyah(surah, ayahNumber, EDITION) }.getOrNull()
            }
            if (ayahs.size != (ayahEnd - ayahStart + 1)) {
                failed++
                continue
            }

            val text = ayahs.joinToString(" ") { it.text.trim() }
            val ayahLabel = if (ayahStart == ayahEnd) "Ayah $ayahStart" else "Ayahs $ayahStart-$ayahEnd"
            val payload = buildJsonObject {
                put("surah", ayahs.first().surah.englishName)
                put("surahMeaning", ayahs.first().surah.englishNameTranslation)
                put("reference", reference)
                put("edition", EDITION)
            }
            val result = stageQuote(
                StagedQuoteCandidate(
                    provider = PROVIDER,
                    providerQuoteId = reference,
                    rawText = text,
                    rawAuthor = null,
                    rawSourceLocation = "Surah $surah, $ayahLabel",
                    sourceId = sourceId,
                    sourceConfidence = "sourced",
                    rawPayload = payload,
                ),
            )
            if (result.inserted) inserted++ else duplicates++
        }

        return ScriptureImportResult(
            sourceId = sourceId,
            quotesInserted = inserted,
            quotesSkippedAsDuplicate = duplicates,
            quotesFailedToFetch = failed,
        )
    }
}

/** "2:255" -> (2, 255, 255). "94:5-6" -> (94, 5, 6). */
internal fun parseReference(reference: String): Triple<Int, Int, Int>? {
    val match = REFERENCE.find(reference) ?: return null
    val surah = match.groupValues[1].toIntOrNull() ?: return null
    val ayahStart = match.groupValues[2].toIntOrNull() ?: return null
    val ayahEnd = match.groupValues[3].toIntOrNull() ?: ayahStart
    return Triple(surah, ayahStart, ayahEnd)
}
