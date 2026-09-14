package no.esotericgames.quotes.server.taote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.esotericgames.quotes.server.importing.ScriptureImportResult
import no.esotericgames.quotes.server.importing.SourceDescriptor
import no.esotericgames.quotes.server.importing.StagedQuoteCandidate
import no.esotericgames.quotes.server.importing.findOrCreateSource
import no.esotericgames.quotes.server.importing.stageQuote

private const val PROVIDER = "tao-te-ching-legge"
private const val RESOURCE_PATH = "/scripture/tao-te-ching-legge.json"
private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class TaoTeChingResource(
    val work: String,
    val translator: String,
    val publicationYear: Int,
    val sourceUrl: String,
    val license: String,
    val chapters: List<TaoTeChingChapter>,
)

@Serializable
private data class TaoTeChingChapter(val chapter: Int, val text: String)

/**
 * Imports James Legge's 1891 translation (public domain) of the Tao Te Ching from a bundled
 * resource file rather than a live fetch — the translation is fixed and never changes, so vendoring
 * it avoids depending on Wikisource being reachable every time this is run. The 81 chapters are
 * already quote-length, so each one is staged as-is; there is no excerpting step like the large,
 * unstructured canons (Bible, Quran, Gutenberg) will need in a later phase.
 */
class TaoTeChingImportService {

    suspend fun import(): ScriptureImportResult {
        val resource = loadResource()
        val sourceId = findOrCreateSource(
            SourceDescriptor(
                title = resource.work,
                typeCode = "scripture",
                year = resource.publicationYear,
                url = resource.sourceUrl,
                citationUnit = "chapter",
                license = resource.license,
                attributionText = "Trans. ${resource.translator} (${resource.publicationYear})",
                translation = resource.translator,
            ),
        )

        var inserted = 0
        var duplicates = 0
        for (chapter in resource.chapters) {
            val payload = buildJsonObject {
                put("work", resource.work)
                put("chapter", chapter.chapter)
                put("translator", resource.translator)
                put("sourceUrl", resource.sourceUrl)
            }
            val result = stageQuote(
                StagedQuoteCandidate(
                    provider = PROVIDER,
                    providerQuoteId = "chapter-${chapter.chapter}",
                    rawText = chapter.text,
                    rawAuthor = "Laozi",
                    rawSourceLocation = chapter.chapter.toString(),
                    sourceId = sourceId,
                    sourceConfidence = "sourced",
                    rawPayload = payload,
                ),
            )
            if (result.inserted) inserted++ else duplicates++
        }

        return ScriptureImportResult(sourceId = sourceId, quotesInserted = inserted, quotesSkippedAsDuplicate = duplicates)
    }

    private fun loadResource(): TaoTeChingResource {
        val stream = javaClass.getResourceAsStream(RESOURCE_PATH)
            ?: error("bundled resource not found: $RESOURCE_PATH")
        val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return json.decodeFromString(TaoTeChingResource.serializer(), content)
    }
}
