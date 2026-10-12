package no.esotericgames.quotes.server.quality

import no.esotericgames.quotes.server.admin.FlagComparison
import no.esotericgames.quotes.server.admin.LevelComparison
import no.esotericgames.quotes.server.admin.QualityAssessmentResponse
import no.esotericgames.quotes.server.admin.QualityComparisonResponse
import no.esotericgames.quotes.server.admin.QualityDisagreement
import kotlin.math.abs

/** One assessment, keyed the way runs are matched: the subject kind plus the hash of the assessed text. */
data class ComparableAssessment(val subjectKind: String, val subjectHash: String, val assessment: QualityAssessmentResponse)

private val VERDICTS_BEST_FIRST = QualityVerdict.entries.sortedByDescending { it.rank }

/**
 * Compares two quality runs on the subjects both assessed successfully (docs/features/quote-quality.md,
 * "Comparing runs"). Pure and in-memory: a run is a few thousand rows at most.
 *
 * Agreement between two runs is not accuracy; the comparison shows where and how much two runners
 * differ, and the disagreements list is what a human reviews to decide which one was right.
 */
object QualityRunComparison {

    fun compare(
        runA: Int,
        runB: Int,
        a: List<ComparableAssessment>,
        b: List<ComparableAssessment>,
        disagreementLimit: Int,
    ): QualityComparisonResponse {
        val succeededA = a.filter { it.assessment.verdict != null }.associateBy { it.subjectKind to it.subjectHash }
        val succeededB = b.filter { it.assessment.verdict != null }.associateBy { it.subjectKind to it.subjectHash }
        val pairs = succeededA.keys.intersect(succeededB.keys).map { key -> succeededA.getValue(key) to succeededB.getValue(key) }

        val verdictIndex = VERDICTS_BEST_FIRST.withIndex().associate { (index, verdict) -> verdict.dbValue to index }
        val matrix = List(VERDICTS_BEST_FIRST.size) { MutableList(VERDICTS_BEST_FIRST.size) { 0 } }
        pairs.forEach { (x, y) -> matrix[verdictIndex.getValue(x.assessment.verdict!!)][verdictIndex.getValue(y.assessment.verdict!!)]++ }

        val keepDrop = List(2) { MutableList(2) { 0 } }
        pairs.forEach { (x, y) -> keepDrop[if (isKeep(x)) 0 else 1][if (isKeep(y)) 0 else 1]++ }

        val gaps = pairs.map { (x, y) -> abs(rank(x) - rank(y)) }
        return QualityComparisonResponse(
            runA = runA,
            runB = runB,
            matched = pairs.size,
            onlyInA = (succeededA.keys - succeededB.keys).size,
            onlyInB = (succeededB.keys - succeededA.keys).size,
            verdicts = VERDICTS_BEST_FIRST.map { it.dbValue },
            verdictMatrix = matrix,
            exactAgreement = share(gaps.count { it == 0 }, pairs.size),
            withinOneAgreement = share(gaps.count { it <= 1 }, pairs.size),
            weightedKappa = quadraticWeightedKappa(matrix),
            keepDropMatrix = keepDrop,
            keepDropKappa = quadraticWeightedKappa(keepDrop),
            levels = listOf(
                levelComparison("standsAlone", pairs) { it.standsAlone },
                levelComparison("completeness", pairs) { it.completeness },
                levelComparison("substance", pairs) { it.substance },
                levelComparison("expression", pairs) { it.expression },
            ),
            flags = flagComparisons(pairs),
            disagreements = pairs
                .filter { (x, y) -> x.assessment.verdict != y.assessment.verdict }
                .sortedWith(compareByDescending<Pair<ComparableAssessment, ComparableAssessment>> { (x, y) -> abs(rank(x) - rank(y)) }
                    .thenBy { it.first.assessment.subjectText })
                .take(disagreementLimit)
                .map { (x, y) ->
                    QualityDisagreement(
                        subjectKind = x.subjectKind,
                        subjectText = x.assessment.subjectText,
                        quoteId = x.assessment.quoteId ?: y.assessment.quoteId,
                        excerptId = x.assessment.excerptId ?: y.assessment.excerptId,
                        a = x.assessment,
                        b = y.assessment,
                    )
                },
        )
    }

    /**
     * Cohen's kappa with quadratic weights over ordered categories; with two categories it equals the
     * unweighted kappa. 1 is perfect agreement, 0 is what chance alone would give.
     */
    fun quadraticWeightedKappa(matrix: List<List<Int>>): Double {
        val size = matrix.size
        val total = matrix.sumOf { it.sum() }.toDouble()
        if (total == 0.0 || size < 2) return 0.0
        val rowTotals = matrix.map { it.sum() }
        val columnTotals = (0 until size).map { column -> matrix.sumOf { it[column] } }
        var observed = 0.0
        var expected = 0.0
        for (i in 0 until size) {
            for (j in 0 until size) {
                val weight = ((i - j) * (i - j)).toDouble() / ((size - 1) * (size - 1))
                observed += weight * matrix[i][j]
                expected += weight * rowTotals[i] * columnTotals[j] / total
            }
        }
        return if (expected == 0.0) 1.0 else 1.0 - observed / expected
    }

    private fun rank(item: ComparableAssessment): Int = QualityVerdict.fromDb(item.assessment.verdict!!)!!.rank

    private fun isKeep(item: ComparableAssessment): Boolean = QualityVerdict.fromDb(item.assessment.verdict!!)!!.isKeep

    private fun share(count: Int, total: Int): Double = if (total == 0) 0.0 else count.toDouble() / total

    private fun levelComparison(
        name: String,
        pairs: List<Pair<ComparableAssessment, ComparableAssessment>>,
        level: (QualityAssessmentResponse) -> Int?,
    ): LevelComparison {
        val values = pairs.mapNotNull { (x, y) -> level(x.assessment)?.let { lx -> level(y.assessment)?.let { ly -> lx to ly } } }
        return LevelComparison(
            name = name,
            meanA = if (values.isEmpty()) 0.0 else values.map { it.first }.average(),
            meanB = if (values.isEmpty()) 0.0 else values.map { it.second }.average(),
            exactAgreement = share(values.count { it.first == it.second }, values.size),
        )
    }

    /** The model's problems in their fixed order, then any custom flags a human used in either run. */
    private fun flagComparisons(pairs: List<Pair<ComparableAssessment, ComparableAssessment>>): List<FlagComparison> {
        val builtIn = QualityProblem.entries.map { it.name }
        val custom = pairs.flatMap { (x, y) -> x.assessment.problems + y.assessment.problems }.distinct().filterNot { it in builtIn }.sorted()
        return (builtIn + custom).map { flag ->
            val inA = pairs.map { flag in it.first.assessment.problems }
            val inB = pairs.map { flag in it.second.assessment.problems }
            FlagComparison(
                flag = flag,
                onlyA = inA.indices.count { inA[it] && !inB[it] },
                onlyB = inA.indices.count { !inA[it] && inB[it] },
                both = inA.indices.count { inA[it] && inB[it] },
            )
        }
    }
}
