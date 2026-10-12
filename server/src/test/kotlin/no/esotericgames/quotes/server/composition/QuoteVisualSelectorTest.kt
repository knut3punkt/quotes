package no.esotericgames.quotes.server.composition

import no.esotericgames.quotes.server.imagegen.BackgroundLayout
import no.esotericgames.quotes.server.imagegen.NormalizedRect
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuoteVisualSelectorTest {

    @Test
    fun `no candidates gives no background and no elements`() {
        val visuals = selector(1).select(QuoteVisualCandidates.EMPTY)

        assertNull(visuals.background)
        assertEquals(emptyList(), visuals.elements)
    }

    @Test
    fun `background is one of the mood images`() {
        val backgrounds = listOf(background(imageId = 1, tagId = 10), background(imageId = 2, tagId = 10), background(3, 11))
        repeat(50) { seed ->
            val visuals = selector(seed).select(QuoteVisualCandidates(backgrounds, emptyList()))
            assertNotNull(visuals.background)
            assertTrue(visuals.background in backgrounds)
        }
    }

    @Test
    fun `each mood tag is picked whatever its image count`() {
        // Tag 10 has five images, tag 11 one: picking the tag first gives both a fair chance.
        val backgrounds = (1..5).map { background(imageId = it, tagId = 10) } + background(imageId = 6, tagId = 11)
        val pickedTags = (0 until 200).map { seed ->
            selector(seed).select(QuoteVisualCandidates(backgrounds, emptyList())).background!!.tagId
        }
        val tag11Share = pickedTags.count { it == 11 } / 200.0
        assertTrue(tag11Share in 0.35..0.65, "tag 11 picked in ${tag11Share * 100}% of draws")
    }

    @Test
    fun `every motif tag gets one of its images, in candidate order`() {
        val elements = listOf(element(imageId = 1, tagId = 30), element(2, 20), element(3, 20), element(4, 10))
        repeat(50) { seed ->
            val visuals = selector(seed).select(QuoteVisualCandidates(listOf(background(9, 90)), elements))
            assertEquals(listOf(30, 20, 10), visuals.elements.map { it.tagId })
            assertTrue(visuals.elements[1].imageId in setOf(2, 3))
        }
    }

    @Test
    fun `a motif tag's images are all used across showings`() {
        val elements = listOf(element(imageId = 1, tagId = 10), element(2, 10), element(3, 10))
        val picked = (0 until 100).map { seed ->
            selector(seed).select(QuoteVisualCandidates(emptyList(), elements)).elements.single().imageId
        }
        assertEquals(setOf(1, 2, 3), picked.toSet())
    }

    @Test
    fun `motifs are capped, keeping the first tags`() {
        val elements = (1..6).map { element(imageId = it, tagId = it * 10) }
        val visuals = selector(1).select(QuoteVisualCandidates(emptyList(), elements))
        assertEquals((1..MAX_MOTIF_ICONS).map { it * 10 }, visuals.elements.map { it.tagId })
    }

    @Test
    fun `motifs are kept without a background`() {
        val elements = listOf(element(imageId = 1, tagId = 10), element(2, 20))
        val visuals = selector(1).select(QuoteVisualCandidates(emptyList(), elements))
        assertNull(visuals.background)
        assertEquals(listOf(1, 2), visuals.elements.map { it.imageId })
    }

    @Test
    fun `grouping keeps an image once per quote and splits backgrounds from elements`() {
        val rows = listOf(
            CandidateRow(quoteId = 1, excerptId = null, background(imageId = 1, tagId = 10), relevance = 3),
            CandidateRow(quoteId = 1, excerptId = null, background(imageId = 1, tagId = 10), relevance = 3),
            CandidateRow(quoteId = 1, excerptId = null, element(imageId = 2, tagId = 20), relevance = 3),
            CandidateRow(quoteId = 2, excerptId = null, element(imageId = 2, tagId = 20), relevance = 2),
        )

        val grouped = groupVisualCandidates(rows, shownExcerpts = mapOf(1 to null, 2 to null))

        assertEquals(listOf(1), grouped.getValue(1).backgrounds.map { it.imageId })
        assertEquals(listOf(2), grouped.getValue(1).elements.map { it.imageId })
        assertEquals(emptyList(), grouped.getValue(2).backgrounds)
        assertEquals(listOf(2), grouped.getValue(2).elements.map { it.imageId })
    }

    @Test
    fun `a shown excerpt keeps its own and whole-quotation tags but not other excerpts' tags`() {
        val rows = listOf(
            CandidateRow(quoteId = 1, excerptId = null, element(imageId = 1, tagId = 10), relevance = 3),
            CandidateRow(quoteId = 1, excerptId = 100, element(imageId = 2, tagId = 20), relevance = 3),
            CandidateRow(quoteId = 1, excerptId = 101, element(imageId = 3, tagId = 30), relevance = 3),
            CandidateRow(quoteId = 2, excerptId = 200, element(imageId = 4, tagId = 40), relevance = 3),
        )

        val grouped = groupVisualCandidates(rows, shownExcerpts = mapOf(1 to 100, 2 to null))

        assertEquals(listOf(1, 2), grouped.getValue(1).elements.map { it.imageId })
        assertEquals(listOf(4), grouped.getValue(2).elements.map { it.imageId })
    }

    @Test
    fun `elements are ordered by their tag's highest relevance, then by tag name`() {
        val rows = listOf(
            CandidateRow(quoteId = 1, excerptId = null, element(imageId = 1, tagId = 10, tagName = "sea"), relevance = 2),
            CandidateRow(quoteId = 1, excerptId = null, element(imageId = 2, tagId = 20, tagName = "lamp"), relevance = 2),
            // Tag 30 is central through the excerpt, though only significant through the whole quotation.
            CandidateRow(quoteId = 1, excerptId = null, element(imageId = 3, tagId = 30, tagName = "seed"), relevance = 2),
            CandidateRow(quoteId = 1, excerptId = 100, element(imageId = 3, tagId = 30, tagName = "seed"), relevance = 3),
        )

        val grouped = groupVisualCandidates(rows, shownExcerpts = mapOf(1 to 100))

        assertEquals(listOf(3, 2, 1), grouped.getValue(1).elements.map { it.imageId })
    }

    private fun selector(seed: Int) = QuoteVisualSelector(Random(seed))

    private fun background(imageId: Int, tagId: Int) = SelectedBackground(
        imageId = imageId,
        tagId = tagId,
        tagName = "mood $tagId",
        width = 2752,
        height = 1536,
        meanLuminance = 0.3,
        dominantColors = emptyList(),
        layout = BackgroundLayout(gridColumns = 0, gridRows = 0, cells = emptyList(), calmRegion = null, textTone = "light"),
        style = null,
        quoteLayout = null,
    )

    private fun element(imageId: Int, tagId: Int, tagName: String = "motif $tagId") = SelectedElement(
        imageId = imageId,
        tagId = tagId,
        tagName = tagName,
        width = 1024,
        height = 1024,
        dominantColors = emptyList(),
        contentBox = NormalizedRect(0.1, 0.1, 0.8, 0.8),
        style = null,
    )
}
