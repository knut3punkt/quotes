package no.esotericgames.quotes.server.wikiquote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import no.esotericgames.quotes.server.WikiquoteAuthorImportResult
import no.esotericgames.quotes.server.WikiquoteImportRequest
import no.esotericgames.quotes.server.WikiquoteImportResponse
import no.esotericgames.quotes.server.importing.StagedQuoteCandidate
import no.esotericgames.quotes.server.importing.sha256Hex
import no.esotericgames.quotes.server.importing.stageQuote
import java.time.Instant

private const val WIKIQUOTE_PAGE_BASE_URL = "https://en.wikiquote.org/wiki/"
private const val PROVIDER = "wikiquote"

private val SECTION_CONFIDENCE = mapOf(
    "Quotes" to "sourced",
    "Attributed" to "attributed",
    "Unsourced" to "unsourced",
)

class WikiquoteImportService(private val client: WikiquoteClient) {

    suspend fun import(request: WikiquoteImportRequest): WikiquoteImportResponse {
        val results = request.authorNames.map { authorName -> importAuthor(authorName, request.sourceConfidence) }
        return WikiquoteImportResponse(results)
    }

    suspend fun searchAuthors(query: String): List<String> =
        if (query.isBlank()) emptyList() else client.searchTitles(query)

    private suspend fun importAuthor(
        requestedName: String,
        confidenceFilter: Set<String>,
    ): WikiquoteAuthorImportResult {
        val resolvedTitle = client.resolveTitle(requestedName)
            ?: return WikiquoteAuthorImportResult(
                requestedName = requestedName,
                resolvedTitle = null,
                found = false,
                quotesInserted = 0,
                quotesSkippedAsDuplicate = 0,
                quotesBySection = emptyMap(),
            )

        val sectionsToFetch = client.listTopLevelSections(resolvedTitle)
            .mapNotNull { section -> SECTION_CONFIDENCE[section.line]?.let { confidence -> section to confidence } }
            .filter { (_, confidence) -> confidence in confidenceFilter }

        var inserted = 0
        var duplicates = 0
        val bySection = mutableMapOf<String, Int>()

        for ((section, confidence) in sectionsToFetch) {
            val content = client.fetchSectionHtml(resolvedTitle, section.index)
            val parsedQuotes = parseQuoteSectionHtml(content.html)
            bySection[confidence] = parsedQuotes.size

            for (parsedQuote in parsedQuotes) {
                val wasInserted = insertQuote(
                    resolvedTitle = resolvedTitle,
                    requestedName = requestedName,
                    revisionId = content.revisionId,
                    confidence = confidence,
                    parsedQuote = parsedQuote,
                )
                if (wasInserted) inserted++ else duplicates++
            }
        }

        return WikiquoteAuthorImportResult(
            requestedName = requestedName,
            resolvedTitle = resolvedTitle,
            found = true,
            quotesInserted = inserted,
            quotesSkippedAsDuplicate = duplicates,
            quotesBySection = bySection,
        )
    }

    private suspend fun insertQuote(
        resolvedTitle: String,
        requestedName: String,
        revisionId: Long,
        confidence: String,
        parsedQuote: ParsedQuote,
    ): Boolean {
        val providerQuoteId = sha256Hex("$resolvedTitle ${parsedQuote.text}")
        val payload = buildRawPayload(
            resolvedTitle = resolvedTitle,
            requestedName = requestedName,
            revisionId = revisionId,
            parsedQuote = parsedQuote,
        )

        val result = stageQuote(
            StagedQuoteCandidate(
                provider = PROVIDER,
                providerQuoteId = providerQuoteId,
                rawText = resolveImportText(parsedQuote),
                rawAuthor = resolvedTitle,
                sourceConfidence = confidence,
                rawPayload = payload,
            ),
        )
        return result.inserted
    }
}

internal fun resolveImportText(parsedQuote: ParsedQuote): String =
    parsedQuote.translationCandidate ?: parsedQuote.text

internal fun buildRawPayload(
    resolvedTitle: String,
    requestedName: String,
    revisionId: Long,
    parsedQuote: ParsedQuote,
): JsonElement = buildJsonObject {
    put("pageTitle", resolvedTitle)
    put("pageUrl", WIKIQUOTE_PAGE_BASE_URL + resolvedTitle.replace(" ", "_"))
    put("revisionId", revisionId)
    put("requestedName", requestedName)
    putJsonArray("sectionPath") { parsedQuote.headingPath.forEach { add(it) } }
    putJsonArray("citations") { parsedQuote.citations.forEach { add(it) } }
    put("translationCandidate", parsedQuote.translationCandidate)
    put("originalText", parsedQuote.text)
    put("fetchedAt", Instant.now().toString())
}
