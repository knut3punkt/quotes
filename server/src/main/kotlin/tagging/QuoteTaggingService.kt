package no.esotericgames.quotes.server.tagging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.admin.GenerateQuoteTagsResponse
import no.esotericgames.quotes.server.admin.QuoteTaggingResult
import no.esotericgames.quotes.server.admin.selectActiveQuoteTags
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteInterpretations
import no.esotericgames.quotes.server.db.QuoteTagAssignments
import no.esotericgames.quotes.server.db.QuoteTaggingAttempts
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.tagging.llm.InferenceOutcome
import no.esotericgames.quotes.server.tagging.llm.TaggingClient
import no.esotericgames.quotes.server.tagging.llm.TaggingRequest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update

private const val ERROR_MESSAGE_MAX_LENGTH = 500
private const val TAGGING_METHOD_VERSION = "v1"

private data class ExcerptRecord(val id: Int, val text: String)

private data class SubjectOutcome(
    val excerptId: Int?,
    val status: String, // "succeeded" | "failed"
    val modelId: String?,
    val tags: List<ValidatedTag>,
    val errorMessage: String?,
)

/**
 * Orchestrates LLM tagging for an admin-selected list of quote ids, structured like
 * [no.esotericgames.quotes.server.interpretation.QuoteInterpretationService]. Each quote is processed
 * independently, so one failure never aborts the rest of the batch.
 *
 * A quote with excerpts (`meetsThresholds = true`) is tagged only through those excerpts; a quote
 * without any is tagged as a whole. Each subject is tagged in its own LLM
 * call, with its own stored interpretations as context, and excerpts also get the parent quote as
 * surrounding context. Once an excerpt has been tagged, the whole quote's earlier LLM tags are removed;
 * admin-added whole-quote tags stay.
 *
 * Re-running is deliberately gentler than interpretation's replace-everything semantics, because admin
 * edits live in the same table:
 * * Only subjects whose call succeeded have their previous LLM tags replaced. A failed call leaves that
 *   subject's earlier tags in place.
 * * Admin-added tags are never removed, and admin-rejected tags are never added back.
 */
class QuoteTaggingService(
    private val inferenceClient: TaggingClient,
    private val config: TaggingConfig,
) {
    suspend fun generateForQuotes(quoteIds: List<Int>): GenerateQuoteTagsResponse =
        GenerateQuoteTagsResponse(quoteIds.map { quoteId -> processOne(quoteId) })

    private suspend fun processOne(quoteId: Int): QuoteTaggingResult {
        val quote = loadQuote(quoteId)
            ?: return QuoteTaggingResult(quoteId, outcome = "notFound", tags = emptyList())
        val excerpts = loadQualifyingExcerpts(quoteId)
        val interpretationsByExcerptId = loadInterpretations(quoteId)
        // Loaded once per quote, so tags coined for earlier quotes in the same batch are already preferred.
        val vocabulary = withContext(Dispatchers.IO) { suspendTransaction { TagVocabulary.loadHint(config.policy) } }

        val subjects = if (excerpts.isEmpty()) {
            listOf(generateForSubject(null, quote.copy(interpretations = interpretationsByExcerptId[null].orEmpty()), vocabulary))
        } else {
            excerpts.map { excerpt ->
                val context = quote.copy(
                    text = excerpt.text,
                    originalContext = quote.text,
                    interpretations = interpretationsByExcerptId[excerpt.id].orEmpty(),
                )
                generateForSubject(excerpt.id, context, vocabulary)
            }
        }

        // Whole-quote LLM tags from before the quote had excerpts are stale once an excerpt has been tagged.
        val clearWholeQuoteLlmTags = subjects.any { it.excerptId != null && it.status == "succeeded" }
        val insertedCount = persistAllAttempts(quoteId, subjects, clearWholeQuoteLlmTags)
        val activeTags = withContext(Dispatchers.IO) { suspendTransaction { selectActiveQuoteTags(listOf(quoteId)) } }
        val outcome = when {
            insertedCount > 0 -> "tagged"
            subjects.any { it.status == "failed" } -> "failed"
            else -> "noTagsFound"
        }
        return QuoteTaggingResult(quoteId, outcome = outcome, tags = activeTags)
    }

    private suspend fun generateForSubject(
        excerptId: Int?,
        context: QuoteTaggingContext,
        vocabulary: VocabularyHint,
    ): SubjectOutcome {
        return try {
            val request = TaggingRequest(
                systemPrompt = TaggingPrompts.systemPrompt(),
                userContent = TaggingPromptBuilder.buildUserContent(context, vocabulary),
                generation = GenerationSettings(
                    model = config.llm.model,
                    temperature = config.llm.temperature,
                    maxOutputTokens = config.llm.maxOutputTokens,
                    reasoningEffort = config.llm.reasoningEffort,
                ),
            )
            when (val outcome = inferenceClient.generateTags(request)) {
                is InferenceOutcome.Success -> {
                    val validated = TaggingValidator.validate(outcome.tagging, config.policy)
                    SubjectOutcome(excerptId, "succeeded", outcome.modelId, validated, null)
                }
                is InferenceOutcome.ConnectionFailure -> SubjectOutcome(excerptId, "failed", null, emptyList(), outcome.message)
                is InferenceOutcome.MalformedResponse -> SubjectOutcome(excerptId, "failed", null, emptyList(), outcome.message)
            }
        } catch (e: Exception) {
            // Last-resort guard: a bug in this pipeline must never abort sibling subjects or other quotes.
            SubjectOutcome(excerptId, "failed", null, emptyList(), e.message ?: e::class.simpleName)
        }
    }

    private suspend fun loadQuote(quoteId: Int): QuoteTaggingContext? = withContext(Dispatchers.IO) {
        suspendTransaction {
            Quotes.leftJoin(Authors).leftJoin(Sources).selectAll().where { Quotes.id eq quoteId }.firstOrNull()
                ?.let {
                    QuoteTaggingContext(
                        text = it[Quotes.text],
                        authorName = it.getOrNull(Authors.name),
                        sourceTitle = it.getOrNull(Sources.title),
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

    private suspend fun loadInterpretations(quoteId: Int): Map<Int?, List<InterpretationContext>> =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                QuoteInterpretations.selectAll().where { QuoteInterpretations.quoteId eq quoteId }
                    .groupBy(
                        { it[QuoteInterpretations.excerptId] },
                        {
                            InterpretationContext(
                                lens = it[QuoteInterpretations.lens],
                                interpretation = it[QuoteInterpretations.interpretation],
                                textualSupport = it[QuoteInterpretations.textualSupport],
                                speculativeness = it[QuoteInterpretations.speculativeness],
                            )
                        },
                    )
            }
        }

    /** Replaces the quote's attempts and its succeeded subjects' LLM tags; returns how many tags were inserted. */
    private suspend fun persistAllAttempts(
        quoteId: Int,
        subjects: List<SubjectOutcome>,
        clearWholeQuoteLlmTags: Boolean,
    ): Int = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteTaggingAttempts.deleteWhere { QuoteTaggingAttempts.quoteId eq quoteId }
            val clearedExcerptIds = subjects.filter { it.status == "succeeded" }.map { it.excerptId } +
                if (clearWholeQuoteLlmTags) listOf(null) else emptyList()
            clearedExcerptIds.distinct().forEach { excerptId ->
                QuoteTagAssignments.deleteWhere {
                    quoteTagSubjectMatches(quoteId, excerptId) and
                        (QuoteTagAssignments.origin eq ORIGIN_LLM) and
                        (QuoteTagAssignments.rejected eq false)
                }
            }
            subjects.sumOf { subject -> persistSubject(quoteId, subject) }
        }
    }

    private fun persistSubject(quoteId: Int, subject: SubjectOutcome): Int {
        val attemptId = QuoteTaggingAttempts.insert {
            it[QuoteTaggingAttempts.quoteId] = quoteId
            it[excerptId] = subject.excerptId
            it[taggingMethodVersion] = TAGGING_METHOD_VERSION
            it[promptVersion] = TaggingPrompts.PROMPT_VERSION
            it[modelId] = subject.modelId
            it[status] = subject.status
            it[errorMessage] = subject.errorMessage?.take(ERROR_MESSAGE_MAX_LENGTH)
        }[QuoteTaggingAttempts.id]

        // Tags already on this subject: admin-added, rejected, or (on a failed subject) earlier LLM tags.
        val occupiedTagIds = QuoteTagAssignments.selectAll()
            .where { quoteTagSubjectMatches(quoteId, subject.excerptId) }
            .map { it[QuoteTagAssignments.tagId] }
            .toMutableSet()

        var inserted = 0
        subject.tags.forEach { tag ->
            val tagId = TagVocabulary.resolveOrCreate(tag.facet, tag.name, tag.breadth, createdBy = ORIGIN_LLM)
            // Two names can resolve to one canonical tag through an alias, so check after resolving.
            if (!occupiedTagIds.add(tagId)) return@forEach
            QuoteTagAssignments.insert {
                it[QuoteTagAssignments.quoteId] = quoteId
                it[excerptId] = subject.excerptId
                it[QuoteTagAssignments.tagId] = tagId
                it[QuoteTagAssignments.attemptId] = attemptId
                it[origin] = ORIGIN_LLM
                it[relevance] = tag.relevance
                it[basis] = tag.basis.dbValue
            }
            inserted++
        }

        QuoteTaggingAttempts.update({ QuoteTaggingAttempts.id eq attemptId }) { it[tagCount] = inserted }
        return inserted
    }
}
