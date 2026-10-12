package no.esotericgames.quotes.server

import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.quality.QualityFlag
import no.esotericgames.quotes.server.quality.QuoteQualityService

/** Rating mode as the TV sees it for one shown quote or excerpt: the run, the rating so far, and the flags to pick. */
@Serializable
data class TvRatingResponse(
    val runLabel: String,
    /** Null while the shown text is not rated yet in this run. */
    val verdict: String?,
    val problems: List<String>,
    val flags: List<QualityFlag>,
)

@Serializable
data class TvRatingRequest(val excerptId: Int? = null, val verdict: String, val problems: List<String> = emptyList())

@Serializable
data class CreateQualityFlagRequest(val label: String)

/**
 * The TV remote's side of human ratings (docs/features/quote-quality.md, "Human ratings on TV"). There is no auth, so
 * the only guard on these public routes is that an admin has turned rating mode on: while it is off, reading returns
 * null and writing fails.
 */
class TvRatingService(
    private val tvDisplayService: TvDisplayService,
    private val quoteQualityService: QuoteQualityService,
) {

    /** Rating mode for [quoteId] shown as [excerptId] (or in full when null), or null while it is off. */
    suspend fun ratingFor(quoteId: Int, excerptId: Int?): TvRatingResponse? {
        val run = tvDisplayService.ratingRun() ?: return null
        return response(run, quoteQualityService.ratingOf(run.id, quoteId, excerptId)?.let { it.verdict to it.problems })
    }

    suspend fun rate(quoteId: Int, request: TvRatingRequest): TvRatingResponse {
        val run = requireRatingRun()
        val stored = quoteQualityService.rate(run.id, quoteId, request.excerptId, request.verdict, request.problems)
        return response(run, stored.verdict to stored.problems)
    }

    suspend fun createFlag(label: String): QualityFlag {
        requireRatingRun()
        return quoteQualityService.createFlag(label)
    }

    private suspend fun requireRatingRun(): TvDisplayRun =
        tvDisplayService.ratingRun() ?: throw IllegalStateException("rating mode is off; turn it on in the admin")

    private suspend fun response(run: TvDisplayRun, rating: Pair<String?, List<String>>?) = TvRatingResponse(
        runLabel = run.label,
        verdict = rating?.first,
        problems = rating?.second.orEmpty(),
        flags = quoteQualityService.flags(),
    )
}
