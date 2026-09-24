package no.esotericgames.quotes.server.extraction

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.ExtractionBatchResponse
import no.esotericgames.quotes.server.admin.QuoteExcerptResponse
import no.esotericgames.quotes.server.admin.QuoteExtractionResult
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteExtractionAttempts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionClient
import no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionRequest
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.extraction.llm.InferenceOutcome
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

private val WHITESPACE_REGEX = Regex("""\s+""")
private const val ERROR_MESSAGE_MAX_LENGTH = 500
private const val EXTRACTION_METHOD_VERSION = "v1"

private data class QuoteRecord(val id: Int, val text: String)

/**
 * Orchestrates extraction for an explicit, admin-selected list of quote ids (triggered from the
 * admin app's multi-select on the approved-quotes page) — not a "process everything" background
 * batch, since there is no job/queue infrastructure in this codebase. Each quote is processed
 * independently so one failure can never abort the rest of the batch or corrupt another quote's
 * data (mirrors `AuthorEnrichmentService`'s tally-and-continue shape).
 *
 * Re-running extraction on a quote replaces its previous attempt/excerpts, including on a failed
 * re-run — the highlighted view always reflects the latest run only.
 */
class QuoteExtractionService(
    private val inferenceClient: ExcerptSelectionClient,
    private val config: ExtractionConfig,
) {
    suspend fun extractForQuotes(quoteIds: List<Int>): ExtractionBatchResponse {
        return ExtractionBatchResponse(quoteIds.map { quoteId -> processOne(quoteId) })
    }

    private suspend fun processOne(quoteId: Int): QuoteExtractionResult {
        val quote = loadQuote(quoteId)
            ?: return QuoteExtractionResult(quoteId, outcome = "notFound", excerpts = emptyList())

        val wordCount = quote.text.trim().split(WHITESPACE_REGEX).count { it.isNotEmpty() }
        if (wordCount < config.policy.minSourceWords) {
            return QuoteExtractionResult(quoteId, outcome = "skippedTooShort", excerpts = emptyList())
        }

        return try {
            val units = segmentSourceIntoUnits(quote.text)
            val request = ExcerptSelectionRequest(
                systemPrompt = ExtractionPrompts.systemPrompt(),
                userContent = ExtractionPromptBuilder.buildUserContent(units),
                generation = GenerationSettings(
                    model = config.llm.model,
                    temperature = config.llm.temperature,
                    maxOutputTokens = config.llm.maxOutputTokens,
                    reasoningEffort = config.llm.reasoningEffort,
                ),
            )
            when (val outcome = inferenceClient.selectExcerpts(request)) {
                is InferenceOutcome.Success -> {
                    val validated = ExtractionValidator.validate(quote.text, units, outcome.candidates, config.policy)
                    val excerpts = persistAttempt(quoteId, "succeeded", outcome.modelId, validated, null)
                    QuoteExtractionResult(
                        quoteId = quoteId,
                        outcome = if (validated.isEmpty()) "noExcerptsFound" else "extracted",
                        excerpts = excerpts,
                    )
                }
                is InferenceOutcome.ConnectionFailure -> {
                    persistAttempt(quoteId, "failed", null, emptyList(), outcome.message)
                    QuoteExtractionResult(quoteId, outcome = "failed", excerpts = emptyList())
                }
                is InferenceOutcome.MalformedResponse -> {
                    persistAttempt(quoteId, "failed", null, emptyList(), outcome.message)
                    QuoteExtractionResult(quoteId, outcome = "failed", excerpts = emptyList())
                }
            }
        } catch (e: Exception) {
            // Last-resort guard: an unforeseen bug in this pipeline must never abort the batch or
            // touch `quotes`. No attempt row is written here since we don't know enough to
            // attribute it meaningfully — it's simply retried next time this quote is selected.
            QuoteExtractionResult(quoteId, outcome = "failed", excerpts = emptyList())
        }
    }

    private suspend fun loadQuote(quoteId: Int): QuoteRecord? = withContext(Dispatchers.IO) {
        suspendTransaction {
            Quotes.selectAll().where { Quotes.id eq quoteId }.firstOrNull()
                ?.let { QuoteRecord(id = it[Quotes.id], text = it[Quotes.text]) }
        }
    }

    private suspend fun persistAttempt(
        quoteId: Int,
        status: String,
        modelId: String?,
        excerpts: List<ValidatedExcerpt>,
        errorMessage: String?,
    ): List<QuoteExcerptResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteExtractionAttempts.deleteWhere { QuoteExtractionAttempts.quoteId eq quoteId }

            val attemptId = QuoteExtractionAttempts.insert {
                it[QuoteExtractionAttempts.quoteId] = quoteId
                it[extractionMethodVersion] = EXTRACTION_METHOD_VERSION
                it[promptVersion] = ExtractionPrompts.PROMPT_VERSION
                it[QuoteExtractionAttempts.modelId] = modelId
                it[QuoteExtractionAttempts.status] = status
                it[excerptCount] = excerpts.size
                it[QuoteExtractionAttempts.errorMessage] = errorMessage?.take(ERROR_MESSAGE_MAX_LENGTH)
            }[QuoteExtractionAttempts.id]

            excerpts.map { excerpt ->
                val excerptId = QuoteExcerpts.insert {
                    it[QuoteExcerpts.attemptId] = attemptId
                    it[QuoteExcerpts.quoteId] = quoteId
                    it[text] = excerpt.text
                    it[startOffset] = excerpt.startOffset
                    it[endOffset] = excerpt.endOffset
                    it[startUnit] = excerpt.startUnit
                    it[endUnit] = excerpt.endUnit
                    it[wordCount] = excerpt.wordCount
                    it[independenceScore] = excerpt.independence
                    it[completenessScore] = excerpt.completeness
                    it[quotabilityScore] = excerpt.quotability
                    it[contextFidelityScore] = excerpt.contextualFidelity
                    it[reason] = excerpt.reason
                    it[meetsThresholds] = excerpt.meetsThresholds
                }[QuoteExcerpts.id]

                QuoteExcerptResponse(
                    id = excerptId,
                    quoteId = quoteId,
                    text = excerpt.text,
                    startOffset = excerpt.startOffset,
                    endOffset = excerpt.endOffset,
                    independence = excerpt.independence,
                    completeness = excerpt.completeness,
                    quotability = excerpt.quotability,
                    contextualFidelity = excerpt.contextualFidelity,
                    reason = excerpt.reason,
                    meetsThresholds = excerpt.meetsThresholds,
                )
            }
        }
    }
}
