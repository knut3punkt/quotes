package no.esotericgames.quotes.server.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HumanRatingTest {

    @Test
    fun `a typed flag is stored under an upper-snake code without accents`() {
        assertEquals("TOO_PREACHY", HumanRating.flagCode("  Too preachy! "))
        assertEquals("CLICHE", HumanRating.flagCode("cliché"))
        assertEquals("NEEDS_A_SOURCE_2", HumanRating.flagCode("needs a source (2)"))
    }

    @Test
    fun `a flag without letters first, or named like a precheck, is refused`() {
        assertFailsWith<IllegalArgumentException> { HumanRating.flagCode("  !!  ") }
        assertFailsWith<IllegalArgumentException> { HumanRating.flagCode("2 short") }
        assertFailsWith<IllegalArgumentException> { HumanRating.flagCode("ellipsis") }
    }

    @Test
    fun `a custom flag keeps a tidy label`() {
        assertEquals("Too preachy", HumanRating.flagLabel("  Too   preachy "))
    }

    @Test
    fun `flags list the model's problems in order, then custom ones by label`() {
        val flags = HumanRating.allFlags(listOf(QualityFlag("ZEALOUS", "zealous", true), QualityFlag("ANGRY", "Angry", true)))

        assertEquals(QualityProblem.entries.map { it.name } + listOf("ANGRY", "ZEALOUS"), flags.map { it.code })
        assertEquals("Needs context", flags.first { it.code == "CONTEXT_DEPENDENT" }.label)
    }

    @Test
    fun `a rating is normalised and checked against known verdicts and flags`() {
        val rating = HumanRating.validate(" Good ", listOf("flat", "FLAT", "TOO_PREACHY"), customCodes = setOf("TOO_PREACHY"))

        assertEquals(QualityVerdict.GOOD, rating.verdict)
        assertEquals(listOf("FLAT", "TOO_PREACHY"), rating.problems)
        assertFailsWith<IllegalArgumentException> { HumanRating.validate("splendid", emptyList(), emptySet()) }
        assertFailsWith<IllegalArgumentException> { HumanRating.validate("good", listOf("TOO_PREACHY"), emptySet()) }
    }
}
