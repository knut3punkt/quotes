package no.esotericgames.quotes.server.extraction

import no.esotericgames.quotes.server.extraction.llm.RawExcerptCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val POLICY = ExtractionPolicy()

private fun candidate(
    startUnit: Int,
    endUnit: Int,
    independence: Int = 90,
    completeness: Int = 90,
    quotability: Int = 90,
    contextualFidelity: Int = 90,
    reason: String = "reason",
) = RawExcerptCandidate(startUnit, endUnit, independence, completeness, quotability, contextualFidelity, reason)

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
    fun `resolves overlapping ranges by keeping the higher-scoring candidate`() {
        val weak = candidate(1, 2, independence = 60, completeness = 60, quotability = 60, contextualFidelity = 60)
        val strong = candidate(1, 1, independence = 95, completeness = 95, quotability = 95, contextualFidelity = 95)

        val result = ExtractionValidator.validate(source, units, listOf(weak, strong), POLICY)

        assertEquals(listOf(1 to 1), result.map { it.startUnit to it.endUnit })
    }

    @Test
    fun `keeps non-overlapping candidates with no maximum count`() {
        val candidates = units.indices.map { index -> candidate(index + 1, index + 1) }

        val result = ExtractionValidator.validate(source, units, candidates, POLICY)

        assertEquals(units.size, result.size)
    }

    @Test
    fun `meetsThresholds is false when a score falls below the policy minimum`() {
        val belowThreshold = candidate(1, 1, independence = 50)

        val result = ExtractionValidator.validate(source, units, listOf(belowThreshold), POLICY)

        assertEquals(1, result.size)
        assertEquals(false, result[0].meetsThresholds)
    }

    @Test
    fun `meetsThresholds is true when every score clears the policy minimum`() {
        val result = ExtractionValidator.validate(source, units, listOf(candidate(1, 1)), POLICY)

        assertEquals(true, result[0].meetsThresholds)
    }
}
