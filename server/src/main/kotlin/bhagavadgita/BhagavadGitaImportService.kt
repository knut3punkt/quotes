package no.esotericgames.quotes.server.bhagavadgita

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.esotericgames.quotes.server.importing.ScriptureImportResult
import no.esotericgames.quotes.server.importing.SourceDescriptor
import no.esotericgames.quotes.server.importing.StagedQuoteCandidate
import no.esotericgames.quotes.server.importing.findOrCreateSource
import no.esotericgames.quotes.server.importing.stageQuote

private const val PROVIDER = "bhagavad-gita-purohit"
private const val CHAPTER_COUNT = 18

// Shri Purohit Swami's translation embeds a leading "chapter.verse " citation in every entry (e.g.
// "18.78 Wherever is..."); stripped since that's already tracked structurally in rawPayload.
private val LEADING_CITATION = Regex("""^\d+\.\d+\s+""")

/**
 * Imports the Bhagavad Gita from the free vedicscriptures.github.io API. Every verse (700 across 18
 * chapters — small enough to import in full, unlike the Bible or Quran) is staged if and only if it
 * carries Shri Purohit Swami's translation; verses without it are skipped rather than falling back
 * to a still-copyrighted commentator, since a missing quote is a much smaller problem than an
 * infringing one.
 */
class BhagavadGitaImportService(private val client: BhagavadGitaClient) {

    suspend fun import(): ScriptureImportResult {
        val sourceId = findOrCreateSource(
            SourceDescriptor(
                title = "Bhagavad Gita",
                typeCode = "scripture",
                citationUnit = "chapter.verse",
                license = "PD",
                attributionText = "Trans. Shri Purohit Swami (1935)",
                translation = "Shri Purohit Swami",
            ),
        )

        var inserted = 0
        var duplicates = 0
        for (chapterNumber in 1..CHAPTER_COUNT) {
            val chapter = client.fetchChapter(chapterNumber)
            for (verseNumber in 1..chapter.versesCount) {
                val verse = client.fetchVerse(chapterNumber, verseNumber)
                val translation = verse.purohit?.et?.let(::stripLeadingCitation)
                if (translation.isNullOrEmpty()) continue

                val payload = buildJsonObject {
                    put("chapter", chapterNumber)
                    put("verse", verseNumber)
                    put("chapterTitle", chapter.translation)
                    put("slok", verse.slok)
                    put("transliteration", verse.transliteration)
                }
                val result = stageQuote(
                    StagedQuoteCandidate(
                        provider = PROVIDER,
                        providerQuoteId = verse.id,
                        rawText = translation,
                        rawAuthor = null,
                        rawSourceLocation = "$chapterNumber.$verseNumber",
                        sourceId = sourceId,
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

internal fun stripLeadingCitation(translation: String): String = translation.trim().replace(LEADING_CITATION, "").trim()
