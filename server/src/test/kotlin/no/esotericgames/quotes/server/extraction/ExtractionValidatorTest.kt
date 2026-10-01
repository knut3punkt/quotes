package no.esotericgames.quotes.server.extraction

import no.esotericgames.quotes.server.extraction.llm.RawExcerptCandidate
import no.esotericgames.quotes.server.extraction.llm.UnitReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val POLICY = ExtractionPolicy()

private fun candidate(
    startUnit: Int,
    endUnit: Int,
    references: List<UnitReference> = emptyList(),
    coreIdea: String = "idea",
    reason: String = "reason",
) = RawExcerptCandidate(startUnit, endUnit, references, coreIdea, reason)

private fun judged(
    startUnit: Int,
    endUnit: Int,
    score: Int = 75,
    meetsThresholds: Boolean = true,
) = ValidatedExcerpt(
    startUnit = startUnit,
    endUnit = endUnit,
    startOffset = 0,
    endOffset = 0,
    text = "",
    wordCount = 10,
    independence = score,
    completeness = score,
    quotability = score,
    contextualFidelity = score,
    reason = "",
    contextSignals = emptyList(),
    judgeNotes = "",
    meetsThresholds = meetsThresholds,
)

class ExtractionValidatorTest {

    private val source = (1..5).joinToString(" ") { sentenceIndex ->
        val words = (1..10).map { "word${sentenceIndex}_$it" }
        (words.first().replaceFirstChar { it.uppercase() } + " " + words.drop(1).joinToString(" ")).trim() + "."
    }
    private val units = segmentSourceIntoUnits(source)

    @Test
    fun `reconstructs excerpt text from original offsets, not from the candidate`() {
        val result = ExtractionValidator.validate(source, units, listOf(candidate(1, 1)), POLICY)

        assertEquals(1, result.size)
        assertEquals(units[0].text, result[0].text)
        assertEquals(source.substring(result[0].startOffset, result[0].endOffset), result[0].text)
    }

    @Test
    fun `rejects a candidate referencing a nonexistent unit without affecting siblings`() {
        val result = ExtractionValidator.validate(source, units, listOf(candidate(1, 99), candidate(2, 2)), POLICY)

        assertEquals(listOf(2 to 2), result.map { it.startUnit to it.endUnit })
    }

    @Test
    fun `rejects startUnit greater than endUnit`() {
        val result = ExtractionValidator.validate(source, units, listOf(candidate(2, 1)), POLICY)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `rejects a candidate outside the acceptable word count range`() {
        val longSource = (1..80).joinToString(" ") { "word$it" } + "."
        val longUnits = segmentSourceIntoUnits(longSource)

        val result = ExtractionValidator.validate(longSource, longUnits, listOf(candidate(1, 1)), POLICY)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `collapses exact duplicate ranges into one`() {
        val result = ExtractionValidator.validate(source, units, listOf(candidate(1, 1), candidate(1, 1)), POLICY)

        assertEquals(1, result.size)
    }

    @Test
    fun `keeps overlapping range variants so the judge can choose between them`() {
        val result = ExtractionValidator.validate(source, units, listOf(candidate(2, 2), candidate(1, 2)), POLICY)

        assertEquals(listOf(2 to 2, 1 to 2), result.map { it.startUnit to it.endUnit })
    }

    @Test
    fun `combines core idea and reason into the stored reason`() {
        val result = ExtractionValidator.validate(source, units, listOf(candidate(1, 1, coreIdea = "Patience teaches", reason = "clear")), POLICY)

        assertEquals("Core idea: Patience teaches — clear", result.single().reason)
    }

    @Test
    fun `carries selector references pointing outside the range as context signals`() {
        val withReference = candidate(2, 2, references = listOf(UnitReference("that remark", 1), UnitReference("inside", 2)))

        val result = ExtractionValidator.validate(source, units, listOf(withReference), POLICY)

        assertEquals(listOf("that remark"), result.single().contextSignals)
    }

    @Test
    fun `resolveOverlaps prefers a passing excerpt over a higher-scoring failing one`() {
        val failing = judged(1, 2, score = 100, meetsThresholds = false)
        val passing = judged(2, 2, score = 75, meetsThresholds = true)

        val result = ExtractionValidator.resolveOverlaps(listOf(failing, passing))

        assertEquals(listOf(2 to 2), result.map { it.startUnit to it.endUnit })
    }

    @Test
    fun `resolveOverlaps keeps the higher-scoring of two passing overlaps`() {
        val weaker = judged(1, 2, score = 75)
        val stronger = judged(1, 1, score = 100)

        val result = ExtractionValidator.resolveOverlaps(listOf(weaker, stronger))

        assertEquals(listOf(1 to 1), result.map { it.startUnit to it.endUnit })
    }

    @Test
    fun `resolveOverlaps keeps non-overlapping excerpts with no maximum count`() {
        val excerpts = (1..5).map { judged(it, it) }

        val result = ExtractionValidator.resolveOverlaps(excerpts)

        assertEquals(5, result.size)
    }
}
