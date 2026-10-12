package no.esotericgames.quotes.server.extraction

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(longClause, units[0].text) // the semicolon falls between the units
        assertEquals("the question was never really in doubt at all.", units[1].text)
    }

    @Test
    fun `a range spanning a clause boundary still contains its punctuation`() {
        val longClause = "the answer was clear to everyone who bothered to look carefully at the evidence"
        val source = "$longClause; the question was never in doubt."

        val units = segmentSourceIntoUnits(source, SegmentationPolicy(maxSentenceWordsBeforeClauseSplit = 10))

        assertEquals(source, source.substring(units.first().startOffset, units.last().endOffset))
    }

    @Test
    fun `does not split at a colon inside a scripture reference or a time`() {
        val longSentence = "The person who exalts himself will be humbled, as Matthew 23:12 says, and the meeting " +
            "at 3:30 went on and on for many more words than anyone could reasonably count here"
        val units = segmentSourceIntoUnits(longSentence, SegmentationPolicy(maxSentenceWordsBeforeClauseSplit = 10))

        assertEquals(listOf(longSentence), units.map { it.text })
    }

    @Test
    fun `does not split after initials written without spaces`() {
        val source = "C.S. Lewis wrote it. The letter arrived late."

        val units = segmentSourceIntoUnits(source)

        assertEquals(listOf("C.S. Lewis wrote it.", "The letter arrived late."), units.map { it.text })
    }

    @Test
    fun `does not split after an initial following an opening parenthesis`() {
        val source = "He wrote (C. S. Lewis once said) that it was so. Then he stopped."

        val units = segmentSourceIntoUnits(source)

        assertEquals(listOf("He wrote (C. S. Lewis once said) that it was so.", "Then he stopped."), units.map { it.text })
    }

    @Test
    fun `the segmentation eval fixtures segment as their descriptions say`() {
        val abbreviations = segmentSourceIntoUnits(fixtureSource("13-abbreviations-and-initials"))
        assertEquals(5, abbreviations.size, abbreviations.joinToString("\n") { it.text })

        val scripture = segmentSourceIntoUnits(fixtureSource("14-scripture-reference"))
        assertEquals(4, scripture.size, scripture.joinToString("\n") { it.text })
        assertEquals("the greatest among you will be your servant.", scripture[1].text)
        assertEquals(false, scripture[0].text.endsWith(":"))
    }

    private fun fixtureSource(id: String): String {
        val json = javaClass.getResource("/extraction-eval/$id.json")!!.readText()
        return Json.parseToJsonElement(json).jsonObject.getValue("sourceText").jsonPrimitive.content
    }

    @Test
    fun `does not split after a rank or a scripture abbreviation`() {
        val source = "Adm. Nelson sailed at dawn. See Matt. 5 for the rest."

        val units = segmentSourceIntoUnits(source)

        assertEquals(listOf("Adm. Nelson sailed at dawn.", "See Matt. 5 for the rest."), units.map { it.text })
    }

    @Test
    fun `splits a long sentence at an em dash`() {
        val longClause = "the argument proceeded slowly through every possible objection that could be raised"
        val source = "$longClause — until finally nothing remained to dispute."

        val units = segmentSourceIntoUnits(source, SegmentationPolicy(maxSentenceWordsBeforeClauseSplit = 8))

        assertEquals(2, units.size)
        assertEquals(longClause, units[0].text)
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
