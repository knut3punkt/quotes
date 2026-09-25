package no.esotericgames.quotes.server.interpretation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.GenerateQuoteInterpretationsResponse
import no.esotericgames.quotes.server.admin.QuoteInterpretationResponse
import no.esotericgames.quotes.server.admin.QuoteInterpretationResult
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteInterpretationAttempts
import no.esotericgames.quotes.server.db.QuoteInterpretations
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.interpretation.llm.InferenceOutcome
import no.esotericgames.quotes.server.interpretation.llm.InterpretationClient
import no.esotericgames.quotes.server.interpretation.llm.InterpretationRequest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

private const val ERROR_MESSAGE_MAX_LENGTH = 500
private const val INTERPRETATION_METHOD_VERSION = "v1"

private data class ExcerptRecord(val id: Int, val text: String)

private data class SubjectOutcome(
    val excerptId: Int?,
    val status: String, // "succeeded" | "failed"
    val modelId: String?,
    val interpretations: List<ValidatedInterpretation>,
    val errorMessage: String?,
)

/**
 * Orchestrates interpretation generation for an explicit, admin-selected list of quote ids, mirroring
 * [no.esotericgames.quotes.server.extraction.QuoteExtractionService] exactly. Each quote is processed
 * independently so one failure can never abort the rest of the batch or corrupt another quote's data.
 *
 * For each quote, generation runs once for the whole quote and once more for every excerpt of that
 * quote with `meetsThresholds = true` — each such "subject" gets its own LLM call, its own validation,
 * and (per docs/features/quote-interpretations.md's "Extracted quotations" section) the excerpt
 * subjects supply the whole quote's text as surrounding context. All of a quote's subjects are
 * generated first, then persisted together: re-running generation on a quote replaces *everything*
 * previously attached to it (whole-quote interpretations and every excerpt's interpretations), even on
 * a run where only some subjects succeed — same replace-the-whole-quote semantics as the original
 * feature, deliberately extended to the whole subject set rather than scoped per subject.
 */
class QuoteInterpretationService(
    private val inferenceClient: InterpretationClient,
    private val llmConfig: InterpretationLlmConfig,
) {
    suspend fun generateForQuotes(quoteIds: List<Int>): GenerateQuoteInterpretationsResponse {
        return GenerateQuoteInterpretationsResponse(quoteIds.map { quoteId -> processOne(quoteId) })
    }

    private suspend fun processOne(quoteId: Int): QuoteInterpretationResult {
        val quote = loadQuote(quoteId)
            ?: return QuoteInterpretationResult(quoteId, outcome = "notFound", interpretations = emptyList())
        val qualifyingExcerpts = loadQualifyingExcerpts(quoteId)

        val subjects = buildList {
            add(generateForSubject(excerptId = null, quote))
            qualifyingExcerpts.forEach { excerpt ->
                add(generateForSubject(excerptId = excerpt.id, quote.copy(text = excerpt.text, originalContext = quote.text)))
            }
        }

        val interpretations = persistAllAttempts(quoteId, subjects)
        return QuoteInterpretationResult(quoteId, outcome = rollupOutcome(subjects, interpretations), interpretations = interpretations)
    }

    private suspend fun generateForSubject(excerptId: Int?, context: QuoteInterpretationContext): SubjectOutcome {
        return try {
            val request = InterpretationRequest(
                systemPrompt = InterpretationPrompts.systemPrompt(),
                userContent = InterpretationPromptBuilder.buildUserContent(context),
                generation = GenerationSettings(
                    model = llmConfig.model,
                    temperature = llmConfig.temperature,
                    maxOutputTokens = llmConfig.maxOutputTokens,
                    reasoningEffort = llmConfig.reasoningEffort,
                ),
            )
            when (val outcome = inferenceClient.generateInterpretations(request)) {
                is InferenceOutcome.Success -> {
                    val validated = InterpretationValidator.validate(outcome.candidates)
                    SubjectOutcome(excerptId, "succeeded", outcome.modelId, validated, null)
                }
                is InferenceOutcome.ConnectionFailure -> SubjectOutcome(excerptId, "failed", null, emptyList(), outcome.message)
                is InferenceOutcome.MalformedResponse -> SubjectOutcome(excerptId, "failed", null, emptyList(), outcome.message)
            }
        } catch (e: Exception) {
            // Last-resort guard: an unforeseen bug in this pipeline must never abort processing of
            // sibling subjects or touch `quotes`/`quote_excerpts`.
            SubjectOutcome(excerptId, "failed", null, emptyList(), e.message ?: e::class.simpleName)
        }
    }

    private fun rollupOutcome(subjects: List<SubjectOutcome>, interpretations: List<QuoteInterpretationResponse>): String = when {
        interpretations.isNotEmpty() -> "generated"
        subjects.any { it.status == "failed" } -> "failed"
        else -> "noInterpretationsFound"
    }

    private suspend fun loadQuote(quoteId: Int): QuoteInterpretationContext? = withContext(Dispatchers.IO) {
        suspendTransaction {
            Quotes.leftJoin(Authors).leftJoin(Sources).selectAll().where { Quotes.id eq quoteId }.firstOrNull()
                ?.let {
                    QuoteInterpretationContext(
                        text = it[Quotes.text],
                        authorName = it.getOrNull(Authors.name),
                        sourceTitle = it.getOrNull(Sources.title),
                        sourceTypeCode = it.getOrNull(Sources.typeCode),
                    )
                }
        }
    }

    private suspend fun loadQualifyingExcerpts(quoteId: Int): List<ExcerptRecord> = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteExcerpts.selectAll()
                .where { (QuoteExcerpts.quoteId eq quoteId) and (QuoteExcerpts.meetsThresholds eq true) }
                .map { ExcerptRecord(id = it[QuoteExcerpts.id], text = it[QuoteExcerpts.text]) }
        }
    }

    private suspend fun persistAllAttempts(
        quoteId: Int,
        subjects: List<SubjectOutcome>,
    ): List<QuoteInterpretationResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteInterpretationAttempts.deleteWhere { QuoteInterpretationAttempts.quoteId eq quoteId }

            subjects.flatMap { subject -> persistSubject(quoteId, subject) }
        }
    }

    private fun persistSubject(quoteId: Int, subject: SubjectOutcome): List<QuoteInterpretationResponse> {
        val attemptId = QuoteInterpretationAttempts.insert {
            it[QuoteInterpretationAttempts.quoteId] = quoteId
            it[QuoteInterpretationAttempts.excerptId] = subject.excerptId
            it[interpretationMethodVersion] = INTERPRETATION_METHOD_VERSION
            it[promptVersion] = InterpretationPrompts.PROMPT_VERSION
            it[QuoteInterpretationAttempts.modelId] = subject.modelId
            it[QuoteInterpretationAttempts.status] = subject.status
            it[interpretationCount] = subject.interpretations.size
            it[QuoteInterpretationAttempts.errorMessage] = subject.errorMessage?.take(ERROR_MESSAGE_MAX_LENGTH)
        }[QuoteInterpretationAttempts.id]

        return subject.interpretations.map { interpretation ->
            val id = QuoteInterpretations.insert {
                it[QuoteInterpretations.attemptId] = attemptId
                it[QuoteInterpretations.quoteId] = quoteId
                it[QuoteInterpretations.excerptId] = subject.excerptId
                it[lens] = interpretation.lens
                it[QuoteInterpretations.interpretation] = interpretation.interpretation
                it[textualSupport] = interpretation.textualSupport
                it[speculativeness] = interpretation.speculativeness
            }[QuoteInterpretations.id]

            QuoteInterpretationResponse(
                id = id,
                quoteId = quoteId,
                excerptId = subject.excerptId,
                lens = interpretation.lens,
                interpretation = interpretation.interpretation,
                textualSupport = interpretation.textualSupport,
                speculativeness = interpretation.speculativeness,
            )
        }
    }
}
