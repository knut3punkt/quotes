package no.esotericgames.quotes.server.quality

import no.esotericgames.quotes.server.admin.QualityAssessmentResponse
import kotlin.test.Test
import kotlin.test.assertEquals

private fun assessed(hash: String, verdict: String?, excerptId: Int? = null, problems: List<String> = emptyList(), substance: Int = 4) =
    ComparableAssessment(
        subjectKind = if (excerptId == null) "quote" else "excerpt",
        subjectHash = hash,
        assessment = QualityAssessmentResponse(
            id = 0,
            runId = 0,
            quoteId = 1,
            excerptId = excerptId,
            subjectKind = if (excerptId == null) "quote" else "excerpt",
            subjectText = "text $hash",
            status = if (verdict == null) "failed" else "succeeded",
            problems = problems,
            substance = substance,
            verdict = verdict,
        ),
    )

class QualityRunComparisonTest {

    @Test
    fun `identical runs agree perfectly`() {
        val run = listOf(assessed("a", "good"), assessed("b", "reject"), assessed("c", "weak"))

        val comparison = QualityRunComparison.compare(1, 2, run, run, disagreementLimit = 10)

        assertEquals(3, comparison.matched)
        assertEquals(1.0, comparison.exactAgreement)
        assertEquals(1.0, comparison.weightedKappa, 1e-9)
        assertEquals(1.0, comparison.keepDropKappa, 1e-9)
        assertEquals(emptyList(), comparison.disagreements)
    }

    @Test
    fun `custom flags a human used are compared after the model's problems`() {
        val a = listOf(assessed("a", "weak", problems = listOf("TOO_PREACHY", "FLAT")))
        val b = listOf(assessed("a", "weak", problems = listOf("FLAT")))

        val flags = QualityRunComparison.compare(1, 2, a, b, disagreementLimit = 10).flags

        assertEquals(QualityProblem.entries.map { it.name } + "TOO_PREACHY", flags.map { it.flag })
        assertEquals(1, flags.last().onlyA)
    }

    @Test
    fun `kappa matches a hand-computed two-by-two example`() {
        // 20 agree keep, 5 A keep / B drop, 10 A drop / B keep, 15 agree drop: po = 0.7, pe = 0.5, kappa = 0.4.
        val kappa = QualityRunComparison.quadraticWeightedKappa(listOf(listOf(20, 5), listOf(10, 15)))

        assertEquals(0.4, kappa, 1e-9)
    }

    @Test
    fun `runs are matched on kind and text hash, and failures and missing subjects are counted apart`() {
        val a = listOf(assessed("same", "good", excerptId = 10), assessed("onlyA", "good"), assessed("failed", null))
        val b = listOf(assessed("same", "weak", excerptId = 99), assessed("onlyB", "good"), assessed("failed", "good"))

        val comparison = QualityRunComparison.compare(1, 2, a, b, disagreementLimit = 10)

        assertEquals(1, comparison.matched) // the excerpt matched although its id changed
        assertEquals(1, comparison.onlyInA)
        assertEquals(2, comparison.onlyInB)
        assertEquals(1, comparison.verdictMatrix[1][3]) // good (row 1) vs weak (column 3)
        assertEquals(1, comparison.disagreements.size)
    }

    @Test
    fun `disagreements are ordered by the size of the gap, and flags are counted per side`() {
        val a = listOf(assessed("x", "excellent", problems = listOf("FLAT")), assessed("y", "good"))
        val b = listOf(assessed("x", "reject", problems = listOf("FLAT", "FRAGMENT")), assessed("y", "acceptable"))

        val comparison = QualityRunComparison.compare(1, 2, a, b, disagreementLimit = 10)

        assertEquals(listOf("text x", "text y"), comparison.disagreements.map { it.subjectText })
        val flat = comparison.flags.single { it.flag == "FLAT" }
        val fragment = comparison.flags.single { it.flag == "FRAGMENT" }
        assertEquals(1, flat.both)
        assertEquals(1, fragment.onlyB)
    }
}
