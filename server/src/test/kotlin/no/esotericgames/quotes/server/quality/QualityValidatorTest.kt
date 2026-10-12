package no.esotericgames.quotes.server.quality

import no.esotericgames.quotes.server.quality.llm.QualityResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun response(
    problems: List<String> = emptyList(),
    verdict: String = "good",
    substance: Int = 4,
    unresolvedReferences: List<String> = emptyList(),
) = QualityResponseDto(
    reading = "  Charm as a way of getting yes.  ",
    unresolvedReferences = unresolvedReferences,
    insight = "Charm wins agreement without asking.",
    problems = problems,
    standsAlone = 5,
    completeness = 5,
    substance = substance,
    expression = 5,
    assessment = "Sharp and self-contained.",
    verdict = verdict,
)

class QualityValidatorTest {

    @Test
    fun `a well-formed response is valid and trimmed`() {
        val valid = assertIs<QualityValidation.Valid>(QualityValidator.validate(response(problems = listOf("flat", "FLAT"))))

        assertEquals("Charm as a way of getting yes.", valid.assessment.reading)
        assertEquals(QualityVerdict.GOOD, valid.assessment.verdict)
        assertEquals(listOf(QualityProblem.FLAT), valid.assessment.problems)
    }

    @Test
    fun `an out-of-range level is rejected, not clamped`() {
        val invalid = assertIs<QualityValidation.Invalid>(QualityValidator.validate(response(substance = 6)))

        assertTrue(invalid.reasons.single().contains("substance"))
    }

    @Test
    fun `an unknown verdict or problem is rejected`() {
        assertIs<QualityValidation.Invalid>(QualityValidator.validate(response(verdict = "great")))
        assertIs<QualityValidation.Invalid>(QualityValidator.validate(response(problems = listOf("BORING"))))
    }

    @Test
    fun `blank references are dropped and the list is capped`() {
        val references = listOf(" ", "this") + (1..20).map { "ref$it" }

        val valid = assertIs<QualityValidation.Valid>(QualityValidator.validate(response(unresolvedReferences = references)))

        assertEquals(10, valid.assessment.unresolvedReferences.size)
        assertEquals("this", valid.assessment.unresolvedReferences.first())
    }
}
