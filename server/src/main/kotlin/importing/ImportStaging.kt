package no.esotericgames.quotes.server.importing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import no.esotericgames.quotes.server.db.ImportedQuotes
import no.esotericgames.quotes.server.db.Quotes
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update

private const val FUZZY_SIMILARITY_THRESHOLD = 0.5

/**
 * One quote as handed off by a source-specific fetch/parse step (Wikiquote, Wikisource, a bundled
 * scripture file, ...) to the shared staging step below. Every importer builds one of these per
 * quote; everything after that point is provider-agnostic.
 */
data class StagedQuoteCandidate(
    val provider: String,
    val providerQuoteId: String,
    val rawText: String,
    val rawAuthor: String?,
    val rawSourceLocation: String? = null,
    val sourceId: Int? = null,
    val sourceConfidence: String?,
    val language: String = "en",
    val rawPayload: JsonElement,
)

data class StageResult(
    val inserted: Boolean,
    val importedQuoteId: Int?,
    val markedDuplicate: Boolean,
)

/**
 * Stages one [candidate] into `imported_quotes`, applying both dedup tiers described in the quote
 * sourcing strategy: an automatic exact-after-normalization match (against both already-approved
 * `quotes` and any prior `imported_quotes` row, from any provider) that immediately flags the new
 * row as a duplicate, and a fuzzy trigram-similarity match that only ever leaves a hint
 * (`possible_duplicate_of_id`) for a human reviewer — it never changes `processing_status` itself.
 *
 * Every source-specific importer should route every candidate through this function rather than
 * inserting into `ImportedQuotes` directly, so dedup behavior stays identical across providers.
 */
suspend fun stageQuote(candidate: StagedQuoteCandidate): StageResult = withContext(Dispatchers.IO) {
    suspendTransaction {
        val normalizedText = normalizeQuoteText(candidate.rawText)

        val insertStatement = ImportedQuotes.insertIgnore {
            it[provider] = candidate.provider
            it[providerQuoteId] = candidate.providerQuoteId
            it[rawText] = candidate.rawText
            it[rawAuthor] = candidate.rawAuthor
            it[rawSourceLocation] = candidate.rawSourceLocation
            it[sourceId] = candidate.sourceId
            it[rawPayload] = candidate.rawPayload
            it[sourceConfidence] = candidate.sourceConfidence
            it[language] = candidate.language
            it[ImportedQuotes.normalizedText] = normalizedText
        }
        if (insertStatement.insertedCount == 0) {
            return@suspendTransaction StageResult(inserted = false, importedQuoteId = null, markedDuplicate = false)
        }
        val newId = insertStatement[ImportedQuotes.id]

        val exactDuplicateOfId = findExactDuplicateImportedQuoteId(normalizedText, newId)
        if (exactDuplicateOfId != null) {
            ImportedQuotes.update({ ImportedQuotes.id eq newId }) {
                it[processingStatus] = "duplicate"
                it[duplicateOfId] = exactDuplicateOfId
            }
            return@suspendTransaction StageResult(inserted = true, importedQuoteId = newId, markedDuplicate = true)
        }

        val fuzzyMatchId = findFuzzyDuplicateImportedQuoteId(normalizedText, newId)
        if (fuzzyMatchId != null) {
            ImportedQuotes.update({ ImportedQuotes.id eq newId }) {
                it[possibleDuplicateOfId] = fuzzyMatchId
            }
        }

        StageResult(inserted = true, importedQuoteId = newId, markedDuplicate = false)
    }
}

/**
 * Exact-after-normalization dedup, preferring a match that's already been approved into `quotes`
 * (whether via the same text or a reviewer's edited text) over a still-pending `imported_quotes`
 * row. Returns an `imported_quotes` id, since that's what `duplicate_of_id` points at.
 */
private fun findExactDuplicateImportedQuoteId(normalizedText: String, excludeId: Int): Int? {
    val matchedQuoteId = Quotes.selectAll()
        .where { Quotes.normalizedText eq normalizedText }
        .firstOrNull()
        ?.get(Quotes.id)
    if (matchedQuoteId != null) {
        val originatingImportId = ImportedQuotes.selectAll()
            .where { ImportedQuotes.quoteId eq matchedQuoteId }
            .firstOrNull()
            ?.get(ImportedQuotes.id)
        if (originatingImportId != null) return originatingImportId
    }

    return ImportedQuotes.selectAll()
        .where { (ImportedQuotes.normalizedText eq normalizedText) and (ImportedQuotes.id neq excludeId) }
        .orderBy(ImportedQuotes.quoteId to SortOrder.DESC_NULLS_LAST)
        .firstOrNull()
        ?.get(ImportedQuotes.id)
}

/**
 * Assistive only: a `pg_trgm` similarity hit never changes `processing_status` on its own, it just
 * points a reviewer at a plausible near-duplicate (paraphrase, re-translation, OCR variance) that
 * the exact-hash tier above can't catch.
 */
private fun findFuzzyDuplicateImportedQuoteId(normalizedText: String, excludeId: Int): Int? {
    val sql = """
        SELECT id FROM imported_quotes
        WHERE id != ? AND similarity(normalized_text, ?) > ?
        ORDER BY similarity(normalized_text, ?) DESC
        LIMIT 1
    """.trimIndent()
    var result: Int? = null
    TransactionManager.current().exec(
        sql,
        args = listOf(
            IntegerColumnType() to excludeId,
            TextColumnType() to normalizedText,
            DoubleColumnType() to FUZZY_SIMILARITY_THRESHOLD,
            TextColumnType() to normalizedText,
        ),
    ) { rs ->
        if (rs.next()) result = rs.getInt(1)
    }
    return result
}
