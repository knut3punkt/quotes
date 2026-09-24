package no.esotericgames.quotes.server.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentationTest {

    @Test
    fun `splits on sentence boundaries`() {
        val source = "Human beings desire certainty. Yet certainty is rarely available. What matters is learning."

        val units = segmentSourceIntoUnits(source)

        assertEquals(
            listOf(
                "Human beings desire certainty.",
                "Yet certainty is rarely available.",
                "What matters is learning.",
            ),
            units.map { it.text },
        )
    }

    @Test
    fun `does not split on an abbreviation followed by a period`() {
        val source = "Dr. Smith said hello. It was a nice day."

        val units = segmentSourceIntoUnits(source)

        assertEquals(listOf("Dr. Smith said hello.", "It was a nice day."), units.map { it.text })
    }

    @Test
    fun `does not split on an initial`() {
        val source = "J. Smith wrote the letter. The letter arrived late."

        val units = segmentSourceIntoUnits(source)

        assertEquals(listOf("J. Smith wrote the letter.", "The letter arrived late."), units.map { it.text })
    }

    @Test
    fun `splits a long sentence at a semicolon`() {
        val longClause = "the answer was clear to everyone who bothered to look carefully at the evidence " +
            "presented over the course of many long and detailed discussions"
        val source = "$longClause; the question was never really in doubt at all."

        val units = segmentSourceIntoUnits(source, SegmentationPolicy(maxSentenceWordsBeforeClauseSplit = 10))

        assertEquals(2, units.size)
        assertTrue(units[0].text.endsWith(";"))
        assertEquals("the question was never really in doubt at all.", units[1].text)
    }

    @Test
    fun `splits a long sentence at an em dash`() {
        val longClause = "the argument proceeded slowly through every possible objection that could be raised"
        val source = "$longClause — until finally nothing remained to dispute."

        val units = segmentSourceIntoUnits(source, SegmentationPolicy(maxSentenceWordsBeforeClauseSplit = 8))

        assertEquals(2, units.size)
        assertTrue(units[0].text.endsWith("—"))
    }

    @Test
    fun `never splits on a comma alone`() {
        val longClause = "the argument proceeded slowly through every possible objection, doubt, and hesitation, " +
            "one after another, without ever truly resolving"
        val source = "$longClause anything of substance."

        val units = segmentSourceIntoUnits(source, SegmentationPolicy(maxSentenceWordsBeforeClauseSplit = 5))

        assertEquals(1, units.size)
    }

    @Test
    fun `unit offsets round-trip against the original source exactly`() {
        val source = "Dr. Smith said: this matters; that does not. A mature mind does not require guarantees."

        val units = segmentSourceIntoUnits(source)

        for (unit in units) {
            assertEquals(unit.text, source.substring(unit.startOffset, unit.endOffset))
        }
    }

    @Test
    fun `unit ids are stable and sequential`() {
        val source = "One. Two. Three."

        val units = segmentSourceIntoUnits(source)

        assertEquals(listOf(1, 2, 3), units.map { it.id })
    }

    @Test
    fun `blank source produces no units`() {
        assertEquals(emptyList(), segmentSourceIntoUnits("   "))
    }
}
