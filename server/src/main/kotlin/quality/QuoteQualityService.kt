package no.esotericgames.quotes.server.quality

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.esotericgames.quotes.server.admin.AssessQualityResponse
import no.esotericgames.quotes.server.admin.CreateQualityRunRequest
import no.esotericgames.quotes.server.admin.QualityAssessmentResponse
import no.esotericgames.quotes.server.admin.QualityComparisonResponse
import no.esotericgames.quotes.server.admin.QualityPromptResponse
import no.esotericgames.quotes.server.admin.QualityRunResponse
import no.esotericgames.quotes.server.admin.QualitySubjectResponse
import no.esotericgames.quotes.server.admin.QuoteFilter
import no.esotericgames.quotes.server.admin.QuoteQualityResult
import no.esotericgames.quotes.server.admin.RejectedUploadItem
import no.esotericgames.quotes.server.admin.UploadAssessmentItem
import no.esotericgames.quotes.server.admin.UploadAssessmentsResponse
import no.esotericgames.quotes.server.admin.applyQuoteFilter
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.QualityCustomFlags
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteQualityAssessments
import no.esotericgames.quotes.server.db.QuoteQualityRuns
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.importing.sha256Hex
import no.esotericgames.quotes.server.quality.llm.QUALITY_JSON_SCHEMA
import no.esotericgames.quotes.server.quality.llm.QualityOutcome
import no.esotericgames.quotes.server.quality.llm.QualityRequest
import no.esotericgames.quotes.server.quality.llm.QualityResponseDto
import no.esotericgames.quotes.server.quality.llm.QuoteQualityClient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/** Prechecks, validation and storage rules; bump when any of them changes. */
const val QUALITY_METHOD_VERSION = "v1"
private const val ERROR_MESSAGE_MAX_LENGTH = 500
private const val SUCCEEDED = "succeeded"
private const val FAILED = "failed"

/** One thing the TV can show: a whole quote, or one of its accepted excerpts. */
private data class QualitySubject(
    val quoteId: Int,
    val excerptId: Int?,
    val kind: SubjectKind,
    val text: String,
    val language: String,
    val authorName: String?,
    val sourceTitle: String?,
) {
    val textHash: String get() = sha256Hex(text)
    val userContent: String get() = QualityPromptBuilder.buildUserContent(text, authorName, sourceTitle)
}

private data class RunRecord(val id: Int, val runner: QualityRunner, val promptVersion: String, val modelId: String?)

/** What a succeeded row stores: a model's or uploader's validated answer, or a human rating (verdict and problems only). */
private data class StoredAssessment(
    val verdict: QualityVerdict,
    val problems: List<String>,
    val reading: String? = null,
    val insight: String? = null,
    val assessment: String? = null,
    val unresolvedReferences: List<String> = emptyList(),
    val standsAlone: Int? = null,
    val completeness: Int? = null,
    val substance: Int? = null,
    val expression: Int? = null,
)

private fun ValidatedQualityAssessment.toStored() = StoredAssessment(
    verdict = verdict,
    problems = problems.map { it.name },
    reading = reading,
    insight = insight,
    assessment = assessment,
    unresolvedReferences = unresolvedReferences,
    standsAlone = standsAlone,
    completeness = completeness,
    substance = substance,
    expression = expression,
)

private data class AssessmentOutcome(
    val stored: StoredAssessment?,
    val modelId: String?,
    val errorMessage: String?,
)

private val uploadJson = Json { ignoreUnknownKeys = true }

/**
 * Quote quality assessment (docs/features/quote-quality.md). Advisory only: it records a verdict per
 * subject and never changes what the TV shows. Unlike the enrichment services, it never replaces an
 * earlier run — every run is kept so runs can be compared. Re-assessing the same subject within a run
 * replaces that one row.
 *
 * A run is either assessed here by the configured local model (`llm`), or uploaded by an external
 * runner that follows the same prompt and schema (`external`). Both go through [QualityValidator] and
 * the same prechecks. A `human` run is rated with the TV remote ([rate]): a verdict and problems only,
 * checked by [HumanRating], with the same prechecks.
 */
class QuoteQualityService(
    private val client: QuoteQualityClient,
    private val llmConfig: QualityLlmConfig,
    private val maxFullQuoteWords: Int,
) {
    fun promptInfo() = QualityPromptResponse(
        promptVersion = QualityPrompts.PROMPT_VERSION,
        methodVersion = QUALITY_METHOD_VERSION,
        systemPrompt = QualityPrompts.systemPrompt(),
        responseSchema = QUALITY_JSON_SCHEMA,
    )

    suspend fun createRun(request: CreateQualityRunRequest): QualityRunResponse = withContext(Dispatchers.IO) {
        val runner = QualityRunner.fromDb(request.runner)
        val label = request.label.trim()
        require(label.isNotEmpty()) { "label is required" }
        val modelId = when (runner) {
            QualityRunner.LLM -> llmConfig.model
            QualityRunner.EXTERNAL -> request.modelId?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("an external run needs the modelId of whoever assesses it")
            QualityRunner.HUMAN -> request.modelId?.trim()?.takeIf { it.isNotEmpty() }
        }
        val runId = suspendTransaction {
            QuoteQualityRuns.insert {
                it[QuoteQualityRuns.label] = label
                it[QuoteQualityRuns.runner] = runner.dbValue
                it[methodVersion] = QUALITY_METHOD_VERSION
                // A human rates without the prompt.
                it[promptVersion] = if (runner == QualityRunner.HUMAN) "none" else QualityPrompts.PROMPT_VERSION
                it[QuoteQualityRuns.modelId] = modelId
                it[settings] = if (runner == QualityRunner.LLM) llmSettingsSnapshot() else null
                it[notes] = request.notes?.trim()?.takeIf { note -> note.isNotEmpty() }
            }[QuoteQualityRuns.id]
        }
        listRuns().first { it.id == runId }
    }

    suspend fun listRuns(): List<QualityRunResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            val counts = QuoteQualityAssessments
                .select(QuoteQualityAssessments.runId, QuoteQualityAssessments.status, QuoteQualityAssessments.verdict, QuoteQualityAssessments.id.count())
                .groupBy(QuoteQualityAssessments.runId, QuoteQualityAssessments.status, QuoteQualityAssessments.verdict)
                .map { Triple(it[QuoteQualityAssessments.runId], it[QuoteQualityAssessments.verdict], it[QuoteQualityAssessments.id.count()]) to it[QuoteQualityAssessments.status] }
                .groupBy { it.first.first }
            QuoteQualityRuns.selectAll().orderBy(QuoteQualityRuns.id to SortOrder.DESC).map { row ->
                val runCounts = counts[row[QuoteQualityRuns.id]].orEmpty()
                QualityRunResponse(
                    id = row[QuoteQualityRuns.id],
                    label = row[QuoteQualityRuns.label],
                    runner = row[QuoteQualityRuns.runner],
                    methodVersion = row[QuoteQualityRuns.methodVersion],
                    promptVersion = row[QuoteQualityRuns.promptVersion],
                    modelId = row[QuoteQualityRuns.modelId],
                    notes = row[QuoteQualityRuns.notes],
                    createdAt = row[QuoteQualityRuns.createdAt].toString(),
                    assessedCount = runCounts.filter { it.second == SUCCEEDED }.sumOf { it.first.third },
                    failedCount = runCounts.filter { it.second == FAILED }.sumOf { it.first.third },
                    verdictCounts = runCounts.filter { it.second == SUCCEEDED }
                        .groupBy({ it.first.second.orEmpty() }, { it.first.third })
                        .mapValues { it.value.sum() },
                )
            }
        }
    }

    /** Assesses each quote and its accepted excerpts with the local model, one quote at a time. */
    suspend fun assessForQuotes(runId: Int, quoteIds: List<Int>): AssessQualityResponse {
        val run = loadRun(runId)
        check(run.runner == QualityRunner.LLM) { "run $runId is ${run.runner.dbValue}, not assessed by the server's model" }
        check(run.promptVersion == QualityPrompts.PROMPT_VERSION && run.modelId == llmConfig.model) {
            "run $runId was made with ${run.promptVersion} on ${run.modelId}; the server now uses " +
                "${QualityPrompts.PROMPT_VERSION} on ${llmConfig.model}. Create a new run."
        }
        return AssessQualityResponse(quoteIds.map { quoteId -> assessOne(runId, quoteId) })
    }

    private suspend fun assessOne(runId: Int, quoteId: Int): QuoteQualityResult {
        val subjects = loadSubjects(listOf(quoteId))
        if (subjects.isEmpty()) return QuoteQualityResult(quoteId, outcome = "notFound", assessments = emptyList())
        val assessments = subjects.map { subject -> persist(runId, subject, assessWithModel(subject)) }
        val outcome = if (assessments.any { it.status == FAILED }) "failed" else "assessed"
        return QuoteQualityResult(quoteId, outcome = outcome, assessments = assessments)
    }

    private suspend fun assessWithModel(subject: QualitySubject): AssessmentOutcome = try {
        val request = QualityRequest(
            systemPrompt = QualityPrompts.systemPrompt(),
            userContent = subject.userContent,
            generation = GenerationSettings(
                model = llmConfig.model,
                temperature = llmConfig.temperature,
                maxOutputTokens = llmConfig.maxOutputTokens,
                reasoningEffort = llmConfig.reasoningEffort,
            ),
        )
        when (val outcome = client.assess(request)) {
            is QualityOutcome.Success -> when (val validation = QualityValidator.validate(outcome.response)) {
                is QualityValidation.Valid -> AssessmentOutcome(validation.assessment.toStored(), outcome.modelId, null)
                is QualityValidation.Invalid -> AssessmentOutcome(null, outcome.modelId, "invalid response: ${validation.reasons.joinToString("; ")}")
            }
            is QualityOutcome.ConnectionFailure -> AssessmentOutcome(null, null, "unreachable at ${llmConfig.baseUrl}: ${outcome.message}")
            is QualityOutcome.MalformedResponse -> AssessmentOutcome(null, null, outcome.message)
        }
    } catch (e: Exception) {
        // Last-resort guard: an unforeseen bug must never abort the sibling subjects.
        AssessmentOutcome(null, null, e.message ?: e::class.simpleName)
    }

    /**
     * The subjects of the quotes matching [filter], with the exact user message the local model gets,
     * so an external runner assesses byte-identical input. [onlyMissing] skips subjects this run has
     * already assessed successfully.
     */
    suspend fun subjects(runId: Int, filter: QuoteFilter, onlyMissing: Boolean): List<QualitySubjectResponse> {
        loadRun(runId)
        val pageSize = filter.pageSize.coerceIn(1, 500)
        val page = filter.page.coerceAtLeast(1)
        val quoteIds = withContext(Dispatchers.IO) {
            suspendTransaction {
                Quotes.leftJoin(Authors).leftJoin(Sources).select(Quotes.id).applyQuoteFilter(filter)
                    .orderBy(Quotes.id to SortOrder.ASC)
                    .map { it[Quotes.id] }
            }
        }
        val assessed = if (onlyMissing) assessedHashes(runId) else emptySet()
        return loadSubjects(quoteIds)
            .filter { (it.kind.dbValue to it.textHash) !in assessed }
            .drop((page - 1) * pageSize)
            .take(pageSize)
            .map { QualitySubjectResponse(it.quoteId, it.excerptId, it.kind.dbValue, it.textHash, it.userContent) }
    }

    /** Stores an external runner's answers after checking each one exactly like a model response. */
    suspend fun upload(runId: Int, items: List<UploadAssessmentItem>): UploadAssessmentsResponse {
        val run = loadRun(runId)
        check(run.runner == QualityRunner.EXTERNAL) { "run $runId is assessed by the server's model; uploads go to an external run" }
        val subjectsByKey = loadSubjects(items.map { it.quoteId }.distinct()).associateBy { it.quoteId to it.excerptId }
        val rejected = mutableListOf<RejectedUploadItem>()
        var stored = 0
        for (item in items) {
            val subject = subjectsByKey[item.quoteId to item.excerptId]
            val reason = when {
                subject == null -> "no such subject (unknown quote, or not an accepted excerpt of it)"
                subject.textHash != item.textHash -> "stale: the text has changed since the subject was fetched"
                else -> null
            }
            if (reason != null || subject == null) {
                rejected += RejectedUploadItem(item.quoteId, item.excerptId, reason ?: "no such subject")
                continue
            }
            val response = try {
                uploadJson.decodeFromJsonElement(QualityResponseDto.serializer(), item.response)
            } catch (e: Exception) {
                rejected += RejectedUploadItem(item.quoteId, item.excerptId, "response does not match the schema: ${e.message}")
                continue
            }
            when (val validation = QualityValidator.validate(response)) {
                is QualityValidation.Valid -> {
                    persist(runId, subject, AssessmentOutcome(validation.assessment.toStored(), run.modelId, null))
                    stored++
                }
                is QualityValidation.Invalid ->
                    rejected += RejectedUploadItem(item.quoteId, item.excerptId, validation.reasons.joinToString("; "))
            }
        }
        return UploadAssessmentsResponse(stored = stored, rejected = rejected)
    }

    /**
     * Stores a human rating of [quoteId] shown as [excerptId] (or in full when null) in human run [runId], replacing an
     * earlier rating of the same text in that run (docs/features/quote-quality.md, "Human ratings on TV").
     */
    suspend fun rate(runId: Int, quoteId: Int, excerptId: Int?, verdict: String, problems: List<String>): QualityAssessmentResponse {
        val run = loadRun(runId)
        check(run.runner == QualityRunner.HUMAN) { "run $runId is ${run.runner.dbValue}; ratings go to a human run" }
        val subject = loadSubject(quoteId, excerptId)
        val customCodes = withContext(Dispatchers.IO) { suspendTransaction { customFlagCodes() } }
        val rating = HumanRating.validate(verdict, problems, customCodes)
        return persist(runId, subject, AssessmentOutcome(StoredAssessment(rating.verdict, rating.problems), run.modelId, null))
    }

    /** The rating of [quoteId] shown as [excerptId] in [runId], matched on the text shown, or null when not rated yet. */
    suspend fun ratingOf(runId: Int, quoteId: Int, excerptId: Int?): QualityAssessmentResponse? {
        val subject = loadSubject(quoteId, excerptId)
        return withContext(Dispatchers.IO) {
            suspendTransaction {
                QuoteQualityAssessments.selectAll()
                    .where {
                        (QuoteQualityAssessments.runId eq runId) and
                            (QuoteQualityAssessments.subjectKind eq subject.kind.dbValue) and
                            (QuoteQualityAssessments.subjectHash eq subject.textHash)
                    }
                    .firstOrNull()
                    ?.toAssessmentResponse()
            }
        }
    }

    /** Every flag a rating may use: the model's problems, then those humans added. */
    suspend fun flags(): List<QualityFlag> = withContext(Dispatchers.IO) {
        suspendTransaction { HumanRating.allFlags(customFlags()) }
    }

    /**
     * The flag for [label], added when it is new. A label that normalises to an existing flag (built-in or custom)
     * returns that flag, so "cliche" and "Cliché" are one flag.
     */
    suspend fun createFlag(label: String): QualityFlag {
        val code = HumanRating.flagCode(label)
        QualityProblem.entries.firstOrNull { it.name == code }?.let { return QualityFlag(it.name, it.label, custom = false) }
        return withContext(Dispatchers.IO) {
            suspendTransaction {
                QualityCustomFlags.insertIgnore {
                    it[QualityCustomFlags.code] = code
                    it[QualityCustomFlags.label] = HumanRating.flagLabel(label)
                }
                customFlags().first { it.code == code }
            }
        }
    }

    private fun customFlags(): List<QualityFlag> =
        QualityCustomFlags.selectAll().map { QualityFlag(it[QualityCustomFlags.code], it[QualityCustomFlags.label], custom = true) }

    private fun customFlagCodes(): Set<String> =
        QualityCustomFlags.select(QualityCustomFlags.code).map { it[QualityCustomFlags.code] }.toSet()

    /** The subject the TV shows for [quoteId] as [excerptId]; the excerpt must be an accepted excerpt of that quote. */
    private suspend fun loadSubject(quoteId: Int, excerptId: Int?): QualitySubject =
        loadSubjects(listOf(quoteId)).firstOrNull { it.excerptId == excerptId }
            ?: throw NoSuchElementException(
                if (excerptId == null) "quote $quoteId not found" else "excerpt $excerptId is not an accepted excerpt of quote $quoteId",
            )

    suspend fun compare(runA: Int, runB: Int, disagreementLimit: Int): QualityComparisonResponse {
        loadRun(runA)
        loadRun(runB)
        val (a, b) = withContext(Dispatchers.IO) {
            suspendTransaction { comparable(runA) to comparable(runB) }
        }
        return QualityRunComparison.compare(runA, runB, a, b, disagreementLimit.coerceIn(0, 2000))
    }

    /** Every assessment of these quotes in [runId], whole quotes and excerpts, for the quote list. */
    suspend fun assessmentsByQuote(runId: Int, quoteIds: List<Int>): Map<Int, List<QualityAssessmentResponse>> =
        if (quoteIds.isEmpty()) {
            emptyMap()
        } else {
            withContext(Dispatchers.IO) {
                suspendTransaction {
                    QuoteQualityAssessments.selectAll()
                        .where { (QuoteQualityAssessments.runId eq runId) and (QuoteQualityAssessments.quoteId inList quoteIds) }
                        .map { it.toAssessmentResponse() }
                        .groupBy { it.quoteId!! }
                }
            }
        }

    private fun comparable(runId: Int): List<ComparableAssessment> =
        QuoteQualityAssessments.selectAll().where { QuoteQualityAssessments.runId eq runId }.map {
            ComparableAssessment(it[QuoteQualityAssessments.subjectKind], it[QuoteQualityAssessments.subjectHash], it.toAssessmentResponse())
        }

    private suspend fun assessedHashes(runId: Int): Set<Pair<String, String>> = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteQualityAssessments.select(QuoteQualityAssessments.subjectKind, QuoteQualityAssessments.subjectHash)
                .where { (QuoteQualityAssessments.runId eq runId) and (QuoteQualityAssessments.status eq SUCCEEDED) }
                .map { it[QuoteQualityAssessments.subjectKind] to it[QuoteQualityAssessments.subjectHash] }
                .toSet()
        }
    }

    private suspend fun loadRun(runId: Int): RunRecord = withContext(Dispatchers.IO) {
        suspendTransaction {
            QuoteQualityRuns.selectAll().where { QuoteQualityRuns.id eq runId }.firstOrNull()?.let {
                RunRecord(
                    id = it[QuoteQualityRuns.id],
                    runner = QualityRunner.fromDb(it[QuoteQualityRuns.runner]),
                    promptVersion = it[QuoteQualityRuns.promptVersion],
                    modelId = it[QuoteQualityRuns.modelId],
                )
            } ?: throw NoSuchElementException("quality run $runId not found")
        }
    }

    /** Each quote, then its accepted excerpts: what the TV can show. */
    private suspend fun loadSubjects(quoteIds: List<Int>): List<QualitySubject> = withContext(Dispatchers.IO) {
        if (quoteIds.isEmpty()) return@withContext emptyList()
        suspendTransaction {
            val quotes = Quotes.leftJoin(Authors).leftJoin(Sources).selectAll()
                .where { Quotes.id inList quoteIds }
                .associateBy { it[Quotes.id] }
            val excerptsByQuote = QuoteExcerpts.selectAll()
                .where { (QuoteExcerpts.quoteId inList quoteIds) and (QuoteExcerpts.meetsThresholds eq true) }
                .orderBy(QuoteExcerpts.startOffset to SortOrder.ASC)
                .groupBy { it[QuoteExcerpts.quoteId] }
            quoteIds.mapNotNull { quotes[it] }.flatMap { row ->
                val quoteId = row[Quotes.id]
                val base = QualitySubject(
                    quoteId = quoteId,
                    excerptId = null,
                    kind = SubjectKind.QUOTE,
                    text = row[Quotes.text],
                    language = row[Quotes.language],
                    authorName = row.getOrNull(Authors.name),
                    sourceTitle = row.getOrNull(Sources.title),
                )
                listOf(base) + excerptsByQuote[quoteId].orEmpty().map { excerpt ->
                    base.copy(excerptId = excerpt[QuoteExcerpts.id], kind = SubjectKind.EXCERPT, text = excerpt[QuoteExcerpts.text])
                }
            }
        }
    }

    private suspend fun persist(runId: Int, subject: QualitySubject, outcome: AssessmentOutcome): QualityAssessmentResponse =
        withContext(Dispatchers.IO) {
            val prechecks = QualityPrechecks.detect(subject.text, subject.language, maxFullQuoteWords).map { it.name }
            val stored = outcome.stored
            suspendTransaction {
                QuoteQualityAssessments.deleteWhere {
                    (QuoteQualityAssessments.runId eq runId) and
                        (QuoteQualityAssessments.subjectKind eq subject.kind.dbValue) and
                        (QuoteQualityAssessments.subjectHash eq subject.textHash)
                }
                val id = QuoteQualityAssessments.insert {
                    it[QuoteQualityAssessments.runId] = runId
                    it[quoteId] = subject.quoteId
                    it[excerptId] = subject.excerptId
                    it[subjectKind] = subject.kind.dbValue
                    it[subjectText] = subject.text
                    it[subjectHash] = subject.textHash
                    it[wordCount] = QualityPrechecks.countWords(subject.text)
                    it[status] = if (stored != null) SUCCEEDED else FAILED
                    it[errorMessage] = outcome.errorMessage?.take(ERROR_MESSAGE_MAX_LENGTH)
                    it[modelId] = outcome.modelId
                    it[reading] = stored?.reading
                    it[insight] = stored?.insight
                    it[assessment] = stored?.assessment
                    it[unresolvedReferences] = stored?.unresolvedReferences.orEmpty()
                    it[problems] = stored?.problems.orEmpty()
                    it[standsAlone] = stored?.standsAlone
                    it[completeness] = stored?.completeness
                    it[substance] = stored?.substance
                    it[expression] = stored?.expression
                    it[verdict] = stored?.verdict?.dbValue
                    it[precheckFlags] = prechecks
                }[QuoteQualityAssessments.id]
                QuoteQualityAssessments.selectAll().where { QuoteQualityAssessments.id eq id }.single().toAssessmentResponse()
            }
        }

    private fun llmSettingsSnapshot() = buildJsonObject {
        put("baseUrl", llmConfig.baseUrl)
        put("model", llmConfig.model)
        put("temperature", llmConfig.temperature)
        put("maxOutputTokens", llmConfig.maxOutputTokens)
        llmConfig.reasoningEffort?.let { put("reasoningEffort", it) }
    }
}

internal fun ResultRow.toAssessmentResponse() = QualityAssessmentResponse(
    id = this[QuoteQualityAssessments.id],
    runId = this[QuoteQualityAssessments.runId],
    quoteId = this[QuoteQualityAssessments.quoteId],
    excerptId = this[QuoteQualityAssessments.excerptId],
    subjectKind = this[QuoteQualityAssessments.subjectKind],
    subjectText = this[QuoteQualityAssessments.subjectText],
    status = this[QuoteQualityAssessments.status],
    errorMessage = this[QuoteQualityAssessments.errorMessage],
    reading = this[QuoteQualityAssessments.reading],
    insight = this[QuoteQualityAssessments.insight],
    assessment = this[QuoteQualityAssessments.assessment],
    unresolvedReferences = this[QuoteQualityAssessments.unresolvedReferences],
    problems = this[QuoteQualityAssessments.problems],
    standsAlone = this[QuoteQualityAssessments.standsAlone],
    completeness = this[QuoteQualityAssessments.completeness],
    substance = this[QuoteQualityAssessments.substance],
    expression = this[QuoteQualityAssessments.expression],
    verdict = this[QuoteQualityAssessments.verdict],
    precheckFlags = this[QuoteQualityAssessments.precheckFlags],
)
