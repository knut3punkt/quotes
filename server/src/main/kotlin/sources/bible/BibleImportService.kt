package no.esotericgames.quotes.server.sources.bible

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.esotericgames.quotes.server.importing.ScriptureImportResult
import no.esotericgames.quotes.server.importing.SourceDescriptor
import no.esotericgames.quotes.server.importing.StagedQuoteCandidate
import no.esotericgames.quotes.server.importing.findOrCreateSource
import no.esotericgames.quotes.server.importing.loadBundledJsonResource
import no.esotericgames.quotes.server.importing.stageQuote

private const val TRANSLATION = "kjv"
private const val PROVIDER = "bible-api-$TRANSLATION"
private const val RESOURCE_PATH = "/scripture/bible-kjv-seed-refs.json"

/**
 * Imports a small, hand-curated seed list of well-known Bible passages (King James Version, public
 * domain per bible-api.com's own license manifest) rather than all 31,102 verses. Unlike the small,
 * already-complete canons in Phase 1, the Bible has no source of pre-curated "quotable" verses — so
 * the seed list itself, not API access, is the real editorial work here, and it's expected to grow
 * over time as a plain, reviewable text file rather than through any automated scoring.
 */
class BibleImportService(private val client: BibleClient) {

    suspend fun import(): ScriptureImportResult {
        val references = loadBundledJsonResource<List<String>>(RESOURCE_PATH)
        val sourceId = findOrCreateSource(
            SourceDescriptor(
                title = "The Bible",
                typeCode = "scripture",
                citationUnit = "book chapter:verse",
                license = "PD",
                attributionText = "King James Version (public domain)",
                translation = "King James Version",
            ),
        )

        var inserted = 0
        var duplicates = 0
        var failed = 0
        for (reference in references) {
            val verse = client.fetchReference(reference, TRANSLATION)
            val text = verse?.text?.trim()?.replace(Regex("\\s+"), " ")
            if (text.isNullOrEmpty()) {
                failed++
                continue
            }

            val payload = buildJsonObject {
                put("reference", verse.reference)
                put("translation", verse.translationId)
                put("translationName", verse.translationName)
            }
            val result = stageQuote(
                StagedQuoteCandidate(
                    provider = PROVIDER,
                    providerQuoteId = verse.reference,
                    rawText = text,
                    rawAuthor = null,
                    rawSourceLocation = verse.reference,
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
