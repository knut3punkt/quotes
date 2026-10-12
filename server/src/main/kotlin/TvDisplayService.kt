package no.esotericgames.quotes.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.db.QuoteQualityAssessments
import no.esotericgames.quotes.server.db.QuoteQualityRuns
import no.esotericgames.quotes.server.db.TvDisplayQualityRuns
import no.esotericgames.quotes.server.db.TvDisplaySettings
import no.esotericgames.quotes.server.quality.QualityRunner
import no.esotericgames.quotes.server.quality.SubjectKind
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.OffsetDateTime

/** How many runs the overlay can compare; more would not fit beside a quote. */
const val MAX_TV_QUALITY_RUNS = 5

@Serializable
data class TvDisplaySettingsRequest(val showQuality: Boolean, val qualityRunIds: List<Int>)

@Serializable
data class TvDisplayRun(val id: Int, val label: String)

/** [ratingRun] is the human run the TV remote rates into, or null while rating mode is off. */
@Serializable
data class TvDisplaySettingsResponse(val showQuality: Boolean, val qualityRuns: List<TvDisplayRun>, val ratingRun: TvDisplayRun?)

/** Turns rating mode on with the human run [runId], or off with null. */
@Serializable
data class TvRatingSettingsRequest(val runId: Int?)

/** One run's assessment of the shown quote or excerpt. */
@Serializable
data class TvRunQuality(
    val runLabel: String,
    /** `succeeded`, `failed`, or `notAssessed` when the run has no assessment of the subject. */
    val status: String,
    val verdict: String?,
    val problems: List<String>,
)

/** The quality overlay for one shown quote or excerpt: its assessment in each chosen run, oldest run first. */
@Serializable
data class TvQualityResponse(
    val runs: List<TvRunQuality>,
    /** The prechecks depend only on the text, so they are given once, merged across the runs. */
    val precheckFlags: List<String>,
)

/** Assessment status for a subject the run never assessed. */
private const val NOT_ASSESSED = "notAssessed"

/**
 * TV display settings set from the admin (docs/features/quote-quality.md, "TV quality overlay"). While the quality
 * overlay is on, the TV shows each shown quote's or excerpt's verdict and flags in the chosen runs. Diagnostic only.
 * While rating mode is on ("Human ratings on TV"), the remote rates what is shown into a human run.
 */
class TvDisplayService {

    suspend fun settings(): TvDisplaySettingsResponse = withContext(Dispatchers.IO) {
        suspendTransaction { currentSettings() }
    }

    /** Turns rating mode on with human run [runId], or off when it is null. */
    suspend fun setRatingRun(runId: Int?): TvDisplaySettingsResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            if (runId != null) {
                val runner = QuoteQualityRuns.select(QuoteQualityRuns.runner).where { QuoteQualityRuns.id eq runId }
                    .singleOrNull()?.get(QuoteQualityRuns.runner)
                    ?: throw IllegalArgumentException("quality run $runId does not exist")
                require(runner == QualityRunner.HUMAN.dbValue) { "the TV rates into a human run; run $runId is $runner" }
            }
            TvDisplaySettings.upsert {
                it[id] = TvDisplaySettings.SINGLE_ROW_ID
                it[ratingRunId] = runId
                it[updatedAt] = OffsetDateTime.now()
            }
            currentSettings()
        }
    }

    /** The human run the TV rates into, or null while rating mode is off. */
    suspend fun ratingRun(): TvDisplayRun? = withContext(Dispatchers.IO) {
        suspendTransaction { currentRatingRun() }
    }

    suspend fun update(request: TvDisplaySettingsRequest): TvDisplaySettingsResponse = withContext(Dispatchers.IO) {
        val runIds = request.qualityRunIds.distinct()
        require(!request.showQuality || runIds.isNotEmpty()) { "showing quality needs at least one quality run" }
        require(runIds.size <= MAX_TV_QUALITY_RUNS) { "the TV shows at most $MAX_TV_QUALITY_RUNS quality runs" }
        suspendTransaction {
            val existing = QuoteQualityRuns.select(QuoteQualityRuns.id)
                .where { QuoteQualityRuns.id inList runIds }
                .map { it[QuoteQualityRuns.id] }
                .toSet()
            val missing = runIds.filterNot { it in existing }
            require(missing.isEmpty()) { "quality runs ${missing.joinToString()} do not exist" }
            TvDisplaySettings.upsert {
                it[id] = TvDisplaySettings.SINGLE_ROW_ID
                it[showQuality] = request.showQuality
                it[updatedAt] = OffsetDateTime.now()
            }
            TvDisplayQualityRuns.deleteAll()
            TvDisplayQualityRuns.batchInsert(runIds) { runId -> this[TvDisplayQualityRuns.runId] = runId }
            currentSettings()
        }
    }

    /**
     * The overlay for [quoteId] shown as [excerptId] (or in full when null), or null while the overlay is off. An
     * excerpt is looked up by its own assessment, a full quote by its whole-quote assessment.
     */
    suspend fun qualityFor(quoteId: Int, excerptId: Int?): TvQualityResponse? = withContext(Dispatchers.IO) {
        suspendTransaction {
            val settings = currentSettings()
            if (!settings.showQuality || settings.qualityRuns.isEmpty()) return@suspendTransaction null
            val subject = if (excerptId != null) {
                (QuoteQualityAssessments.subjectKind eq SubjectKind.EXCERPT.dbValue) and
                    (QuoteQualityAssessments.excerptId eq excerptId)
            } else {
                (QuoteQualityAssessments.subjectKind eq SubjectKind.QUOTE.dbValue) and
                    (QuoteQualityAssessments.quoteId eq quoteId)
            }
            val assessments = QuoteQualityAssessments.selectAll()
                .where { (QuoteQualityAssessments.runId inList settings.qualityRuns.map { it.id }) and subject }
                .associateBy { it[QuoteQualityAssessments.runId] }
            TvQualityResponse(
                runs = settings.qualityRuns.map { run ->
                    val assessment = assessments[run.id]
                    TvRunQuality(
                        runLabel = run.label,
                        status = assessment?.get(QuoteQualityAssessments.status) ?: NOT_ASSESSED,
                        verdict = assessment?.get(QuoteQualityAssessments.verdict),
                        problems = assessment?.get(QuoteQualityAssessments.problems).orEmpty(),
                    )
                },
                precheckFlags = settings.qualityRuns
                    .flatMap { run -> assessments[run.id]?.get(QuoteQualityAssessments.precheckFlags).orEmpty() }
                    .distinct(),
            )
        }
    }

    private fun currentSettings(): TvDisplaySettingsResponse {
        val showQuality = TvDisplaySettings.selectAll().singleOrNull()?.get(TvDisplaySettings.showQuality) ?: false
        val runs = (TvDisplayQualityRuns innerJoin QuoteQualityRuns)
            .select(QuoteQualityRuns.id, QuoteQualityRuns.label)
            .orderBy(QuoteQualityRuns.id, SortOrder.ASC)
            .map { TvDisplayRun(it[QuoteQualityRuns.id], it[QuoteQualityRuns.label]) }
        return TvDisplaySettingsResponse(showQuality, runs, currentRatingRun())
    }

    private fun currentRatingRun(): TvDisplayRun? = (TvDisplaySettings innerJoin QuoteQualityRuns)
        .select(QuoteQualityRuns.id, QuoteQualityRuns.label)
        .singleOrNull()
        ?.let { TvDisplayRun(it[QuoteQualityRuns.id], it[QuoteQualityRuns.label]) }
}

/** The human run the TV rates into, or null while rating mode is off. Must run inside a transaction. */
fun activeRatingRunId(): Int? = TvDisplaySettings.select(TvDisplaySettings.ratingRunId).singleOrNull()?.get(TvDisplaySettings.ratingRunId)
