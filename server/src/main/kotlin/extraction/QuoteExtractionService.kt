package no.esotericgames.quotes.server.extraction

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.ExtractionBatchResponse
import no.esotericgames.quotes.server.admin.QuoteExcerptResponse
import no.esotericgames.quotes.server.admin.QuoteExtractionResult
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteExtractionAttempts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.extraction.llm.ExcerptJudgeClient
import no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionClient
import no.esotericgames.quotes.server.extraction.llm.ExcerptSelectionRequest
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.extraction.llm.InferenceOutcome
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

private val WHITESPACE_REGEX = Regex("""\s+""")
private const val ERROR_MESSAGE_MAX_LENGTH = 500

/** `quote_excerpts.context_signals` stores one flagged phrase per line (see V21). */
const val CONTEXT_SIGNAL_SEPARATOR = "\n"
// v1: single selection call that also self-scored. v2: broad selector plus a separate judge pass.
private const val EXTRACTION_METHOD_VERSION = "v2"

private val log = LoggerFactory.getLogger(QuoteExtractionService::class.java)

private data class QuoteRecord(val id: Int, val text: String)

private data class JudgedCandidates(
    val excerpts: List<ValidatedExcerpt>,
    val judgeModelId: String?,
    val errors: List<String>,
    val unreachableMessage: String? = null,
)

/**
 * Orchestrates extraction for an explicit, admin-selected list of quote ids (triggered from the
 * admin app's multi-select on the approved-quotes page) — not a "process everything" background
 * batch, since there is no job/queue infrastructure in this codebase. Each quote is processed
 * independently so one failure can never abort the rest of the batch or corrupt another quote's
 * data (mirrors `AuthorEnrichmentService`'s tally-and-continue shape).
 *
 * Per quote: a broad selection call proposes candidates, deterministic validation reconstructs them,
 * the [ExcerptJudge] reviews each one, and overlaps are resolved by the judge's verdict. A judge
 * response that is malformed for one candidate only drops that candidate (noted in the attempt's
 * error message); an unreachable judge fails the whole attempt, since every later call would too.
 *
 * Re-running extraction on a quote replaces its previous attempt/excerpts, including on a failed
 * re-run — the highlighted view always reflects the latest run only.
 */
class QuoteExtractionService(
    private val inferenceClient: ExcerptSelectionClient,
    judgeClient: ExcerptJudgeClient,
    private val config: ExtractionConfig,
) {
    private val judge = ExcerptJudge(
        client = judgeClient,
        generation = GenerationSettings(
            model = config.judge.model,
            temperature = config.judge.temperature,
            maxOutputTokens = config.judge.maxOutputTokens,
            reasoningEffort = config.judge.reasoningEffort,
        ),
        policy = config.policy,
    )

    init {
        log.info(
            "Excerpt extraction: selector {} @ {}, judge {} @ {}",
            config.llm.model, config.llm.baseUrl, config.judge.model, config.judge.baseUrl,
        )
    }

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
                    val candidates = ExtractionValidator.validate(quote.text, units, outcome.candidates, config.policy)
                    val judged = judgeAll(quote.text, candidates)
                    judged.unreachableMessage?.let {
                        return persistFailure(quoteId, outcome.modelId, "judge unreachable at ${config.judge.baseUrl}: $it")
                    }
                    judged.errors.forEach { log.warn("Excerpt extraction for quote {}: {}", quoteId, it) }
                    val resolved = ExtractionValidator.resolveOverlaps(judged.excerpts)
                    val excerpts = persistAttempt(
                        quoteId = quoteId,
                        status = "succeeded",
                        modelId = outcome.modelId,
                        judgeModelId = judged.judgeModelId,
                        excerpts = resolved,
                        errorMessage = judged.errors.joinToString("; ").ifEmpty { null },
                    )
                    QuoteExtractionResult(
                        quoteId = quoteId,
                        outcome = if (resolved.none { it.meetsThresholds }) "noExcerptsFound" else "extracted",
                        excerpts = excerpts,
                    )
                }
                is InferenceOutcome.ConnectionFailure ->
                    persistFailure(quoteId, null, "selector unreachable at ${config.llm.baseUrl}: ${outcome.message}")
                is InferenceOutcome.MalformedResponse -> persistFailure(quoteId, null, "selector: ${outcome.message}")
            }
        } catch (e: Exception) {
            // Last-resort guard: an unforeseen bug in this pipeline must never abort the batch or
            // touch `quotes`. No attempt row is written here since we don't know enough to
            // attribute it meaningfully — it's simply retried next time this quote is selected.
            log.error("Excerpt extraction for quote {} failed unexpectedly", quoteId, e)
            QuoteExtractionResult(quoteId, outcome = "failed", excerpts = emptyList())
        }
    }

    /** Stops at the first unreachable-judge result, so the caller can fail the attempt as a whole. */
    private suspend fun judgeAll(sourceText: String, candidates: List<ExcerptCandidate>): JudgedCandidates {
        val excerpts = mutableListOf<ValidatedExcerpt>()
        val errors = mutableListOf<String>()
        var judgeModelId: String? = null
        for (candidate in candidates) {
            when (val judgment = judge.judge(sourceText, candidate)) {
                is ExcerptJudgment.Judged -> {
                    excerpts += judgment.excerpt
                    judgeModelId = judgment.modelId ?: judgeModelId
                }
                is ExcerptJudgment.Malformed -> errors += "judge, units ${candidate.startUnit}-${candidate.endUnit}: ${judgment.message}"
                is ExcerptJudgment.Unreachable -> return JudgedCandidates(excerpts, judgeModelId, errors, judgment.message)
            }
        }
        return JudgedCandidates(excerpts, judgeModelId, errors)
    }

    private suspend fun persistFailure(quoteId: Int, modelId: String?, message: String): QuoteExtractionResult {
        log.warn("Excerpt extraction for quote {} failed: {}", quoteId, message)
        persistAttempt(quoteId, "failed", modelId, null, emptyList(), message)
        return QuoteExtractionResult(quoteId, outcome = "failed", excerpts = emptyList())
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
        judgeModelId: String?,
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
                it[judgePromptVersion] = if (status == "succeeded") ExcerptJudgePrompts.PROMPT_VERSION else null
                it[QuoteExtractionAttempts.judgeModelId] = judgeModelId
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
                    it[contextSignals] = excerpt.contextSignals.joinToString(CONTEXT_SIGNAL_SEPARATOR).ifEmpty { null }
                    it[judgeNotes] = excerpt.judgeNotes
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
                    contextSignals = excerpt.contextSignals,
                    judgeNotes = excerpt.judgeNotes,
                    meetsThresholds = excerpt.meetsThresholds,
                )
            }
        }
    }
}
