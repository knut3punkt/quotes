package no.esotericgames.quotes.server.extraction

import no.esotericgames.quotes.server.extraction.llm.ExcerptJudgeClient
import no.esotericgames.quotes.server.extraction.llm.FidelityVerdict
import no.esotericgames.quotes.server.extraction.llm.GenerationSettings
import no.esotericgames.quotes.server.extraction.llm.JudgeOutcome
import no.esotericgames.quotes.server.extraction.llm.JudgeRequest
import no.esotericgames.quotes.server.extraction.llm.StandaloneVerdict

private const val MIN_LEVEL = 1
private const val MAX_LEVEL = 5
private const val SCORE_PER_LEVEL = 25

sealed interface ExcerptJudgment {
    data class Judged(val excerpt: ValidatedExcerpt, val modelId: String?) : ExcerptJudgment

    /** The judge's server could not be reached; later candidates would fail the same way. */
    data class Unreachable(val message: String) : ExcerptJudgment

    /** This one candidate's judge response was unusable; other candidates are unaffected. */
    data class Malformed(val message: String) : ExcerptJudgment
}

/**
 * The strict precision filter behind the broad selector (docs/features/quote-extraction.md's
 * "Judge pass"). The judge's verdict, not the selector's, decides [ValidatedExcerpt.meetsThresholds].
 *
 * The standalone call is blind — it sees only the excerpt text, never the source. A model that has
 * read the surrounding passage always "knows" what "this" refers to, so it cannot judge whether a
 * first-time reader would; that curse of knowledge was the main reason excerpts with dangling
 * references scored high on independence when the selector graded itself. The fidelity call, which
 * does need the source, only runs for candidates that pass the blind call.
 */
class ExcerptJudge(
    private val client: ExcerptJudgeClient,
    private val generation: GenerationSettings,
    private val policy: ExtractionPolicy,
) {
    suspend fun judge(sourceText: String, candidate: ExcerptCandidate): ExcerptJudgment {
        val standaloneRequest = JudgeRequest(
            systemPrompt = ExcerptJudgePrompts.standaloneSystemPrompt(),
            userContent = ExcerptJudgePrompts.buildStandaloneUserContent(candidate.text, candidate.contextSignals),
            generation = generation,
        )
        val standalone = when (val outcome = client.judgeStandalone(standaloneRequest)) {
            is JudgeOutcome.Success -> outcome
            is JudgeOutcome.ConnectionFailure -> return ExcerptJudgment.Unreachable(outcome.message)
            is JudgeOutcome.MalformedResponse -> return ExcerptJudgment.Malformed(outcome.message)
        }

        if (!passesStandalone(standalone.verdict, policy)) {
            return ExcerptJudgment.Judged(combineVerdicts(candidate, standalone.verdict, null, policy), standalone.modelId)
        }

        val fidelityRequest = JudgeRequest(
            systemPrompt = ExcerptJudgePrompts.fidelitySystemPrompt(),
            userContent = ExcerptJudgePrompts.buildFidelityUserContent(sourceText, candidate.text, standalone.verdict.whatItIsAbout),
            generation = generation,
        )
        return when (val outcome = client.judgeFidelity(fidelityRequest)) {
            is JudgeOutcome.Success ->
                ExcerptJudgment.Judged(combineVerdicts(candidate, standalone.verdict, outcome.verdict, policy), outcome.modelId)
            is JudgeOutcome.ConnectionFailure -> ExcerptJudgment.Unreachable(outcome.message)
            is JudgeOutcome.MalformedResponse -> ExcerptJudgment.Malformed(outcome.message)
        }
    }

    companion object {
        /** Maps an anchored 1-5 level onto the stored 0-100 scale (1 -> 0, 3 -> 50, 5 -> 100), clamping out-of-range levels. */
        fun levelToScore(level: Int): Int = (level.coerceIn(MIN_LEVEL, MAX_LEVEL) - MIN_LEVEL) * SCORE_PER_LEVEL

        /**
         * Hard rules on top of the levels: any reference the blind reader could not resolve fails
         * independence, and an empty insight fails quotability, regardless of the levels given —
         * those free-text fields are the model's actual reading, and are harder to inflate than a number.
         */
        fun passesStandalone(verdict: StandaloneVerdict, policy: ExtractionPolicy): Boolean =
            verdict.unresolvedReferences.none { it.isNotBlank() } &&
                verdict.insight.isNotBlank() &&
                levelToScore(verdict.standsAlone) >= policy.minIndependence &&
                levelToScore(verdict.completeness) >= policy.minCompleteness &&
                levelToScore(verdict.quotability) >= policy.minQuotability

        fun combineVerdicts(
            candidate: ExcerptCandidate,
            standalone: StandaloneVerdict,
            fidelity: FidelityVerdict?,
            policy: ExtractionPolicy,
        ): ValidatedExcerpt {
            val fidelityScore = fidelity?.let { levelToScore(it.fidelity) }
            val meetsThresholds = passesStandalone(standalone, policy) &&
                fidelityScore != null && fidelityScore >= policy.minContextualFidelity

            return ValidatedExcerpt(
                startUnit = candidate.startUnit,
                endUnit = candidate.endUnit,
                startOffset = candidate.startOffset,
                endOffset = candidate.endOffset,
                text = candidate.text,
                wordCount = candidate.wordCount,
                independence = levelToScore(standalone.standsAlone),
                completeness = levelToScore(standalone.completeness),
                quotability = levelToScore(standalone.quotability),
                contextualFidelity = fidelityScore,
                reason = candidate.reason,
                contextSignals = candidate.contextSignals,
                judgeNotes = judgeNotes(standalone, fidelity),
                meetsThresholds = meetsThresholds,
            )
        }

        private fun judgeNotes(standalone: StandaloneVerdict, fidelity: FidelityVerdict?): String = buildList {
            add("Blind reading: ${standalone.whatItIsAbout.trim()}")
            add("Insight: ${standalone.insight.trim().ifEmpty { "(none)" }}")
            val unresolved = standalone.unresolvedReferences.filter { it.isNotBlank() }
            if (unresolved.isNotEmpty()) add("Unresolved: ${unresolved.joinToString(", ")}")
            if (fidelity == null) {
                add("Fidelity not checked (failed the blind review)")
            } else {
                add("In source: ${fidelity.meaningInSource.trim()}")
                if (fidelity.reason.isNotBlank()) add("Fidelity: ${fidelity.reason.trim()}")
            }
        }.joinToString("\n")
    }
}
