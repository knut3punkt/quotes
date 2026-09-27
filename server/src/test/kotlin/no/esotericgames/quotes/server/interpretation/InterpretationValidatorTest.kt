package no.esotericgames.quotes.server.interpretation

import no.esotericgames.quotes.server.interpretation.llm.MAX_INTERPRETATIONS
import no.esotericgames.quotes.server.interpretation.llm.MAX_INTERPRETATION_LENGTH
import no.esotericgames.quotes.server.interpretation.llm.MAX_LENS_LENGTH
import no.esotericgames.quotes.server.interpretation.llm.RawInterpretationCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun candidate(
    lens: String = "Psychological",
    interpretation: String = "A plausible reading of the quotation.",
    textualSupport: Int = 80,
    speculativeness: Int = 30,
) = RawInterpretationCandidate(lens, interpretation, textualSupport, speculativeness)

class InterpretationValidatorTest {

    @Test
    fun `passes through a well-formed candidate`() {
        val result = InterpretationValidator.validate(listOf(candidate()))

        assertEquals(1, result.size)
        assertEquals("Psychological", result[0].lens)
    }

    @Test
    fun `an empty candidate list passes through as empty`() {
        assertTrue(InterpretationValidator.validate(emptyList()).isEmpty())
    }

    @Test
    fun `rejects a blank lens without affecting siblings`() {
        val result = InterpretationValidator.validate(listOf(candidate(lens = "   "), candidate(interpretation = "A different reading.")))

        assertEquals(1, result.size)
        assertEquals("A different reading.", result[0].interpretation)
    }

    @Test
    fun `rejects a blank interpretation without affecting siblings`() {
        val result = InterpretationValidator.validate(listOf(candidate(interpretation = ""), candidate(lens = "Ethical")))

        assertEquals(1, result.size)
        assertEquals("Ethical", result[0].lens)
    }

    @Test
    fun `rejects a lens exceeding the maximum length`() {
        val result = InterpretationValidator.validate(listOf(candidate(lens = "x".repeat(MAX_LENS_LENGTH + 1))))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `rejects an interpretation exceeding the maximum length`() {
        val result = InterpretationValidator.validate(listOf(candidate(interpretation = "x".repeat(MAX_INTERPRETATION_LENGTH + 1))))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `clamps out-of-range scores into 0-100`() {
        val result = InterpretationValidator.validate(listOf(candidate(textualSupport = 150, speculativeness = -20)))

        assertEquals(100, result[0].textualSupport)
        assertEquals(0, result[0].speculativeness)
    }

    @Test
    fun `drops an exact duplicate normalized interpretation text, keeping the first`() {
        val result = InterpretationValidator.validate(
            listOf(
                candidate(lens = "Ethical", interpretation = "Habits shape identity."),
                candidate(lens = "Psychological", interpretation = "  Habits   shape identity.  "),
            ),
        )

        assertEquals(1, result.size)
        assertEquals("Ethical", result[0].lens)
    }

    @Test
    fun `caps the result at the maximum interpretation count`() {
        val candidates = (1..MAX_INTERPRETATIONS + 3).map { candidate(interpretation = "Reading number $it.") }

        val result = InterpretationValidator.validate(candidates)

        assertEquals(MAX_INTERPRETATIONS, result.size)
    }
}
