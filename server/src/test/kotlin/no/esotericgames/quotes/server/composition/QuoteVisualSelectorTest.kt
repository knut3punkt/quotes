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
        val visuals = QuoteVisualSelector(Random(1)).select(QuoteVisualCandidates.EMPTY)

        assertNull(visuals.background)
        assertEquals(emptyList(), visuals.elements)
    }

    @Test
    fun `background is one of the mood images`() {
        val backgrounds = listOf(background(imageId = 1, tagId = 10), background(imageId = 2, tagId = 10), background(3, 11))
        repeat(50) { seed ->
            val visuals = QuoteVisualSelector(Random(seed)).select(QuoteVisualCandidates(backgrounds, emptyList()))
            assertNotNull(visuals.background)
            assertTrue(visuals.background in backgrounds)
        }
    }

    @Test
    fun `each mood tag is picked whatever its image count`() {
        // Tag 10 has five images, tag 11 one: picking the tag first gives both a fair chance.
        val backgrounds = (1..5).map { background(imageId = it, tagId = 10) } + background(imageId = 6, tagId = 11)
        val pickedTags = (0 until 200).map { seed ->
            QuoteVisualSelector(Random(seed)).select(QuoteVisualCandidates(backgrounds, emptyList())).background!!.tagId
        }
        val tag11Share = pickedTags.count { it == 11 } / 200.0
        assertTrue(tag11Share in 0.35..0.65, "tag 11 picked in ${tag11Share * 100}% of draws")
    }

    @Test
    fun `elements come from distinct motif tags and never exceed four`() {
        val elements = (1..6).flatMap { tagId -> listOf(element(imageId = tagId * 10, tagId), element(tagId * 10 + 1, tagId)) }
        val counts = (0 until 200).map { seed ->
            val visuals = QuoteVisualSelector(Random(seed)).select(QuoteVisualCandidates(emptyList(), elements))
            assertEquals(visuals.elements.size, visuals.elements.map { it.tagId }.distinct().size)
            visuals.elements.size
        }
        assertEquals((1..MAX_COLLAGE_ELEMENTS).toSet(), counts.toSet())
    }

    @Test
    fun `fewer motif tags than the drawn count gives all of them`() {
        val elements = listOf(element(imageId = 1, tagId = 1), element(imageId = 2, tagId = 2))
        val counts = (0 until 100).map { seed ->
            QuoteVisualSelector(Random(seed)).select(QuoteVisualCandidates(emptyList(), elements)).elements.size
        }
        // N = 1 gives one element; N = 2..4 gives both.
        assertEquals(setOf(1, 2), counts.toSet())
    }

    @Test
    fun `grouping keeps an image once per quote and splits backgrounds from elements`() {
        val rows = listOf(
            1 to background(imageId = 1, tagId = 10),
            1 to background(imageId = 1, tagId = 10),
            1 to element(imageId = 2, tagId = 20),
            2 to element(imageId = 2, tagId = 20),
        )

        val grouped = groupVisualCandidates(rows)

        assertEquals(listOf(1), grouped.getValue(1).backgrounds.map { it.imageId })
        assertEquals(listOf(2), grouped.getValue(1).elements.map { it.imageId })
        assertEquals(emptyList(), grouped.getValue(2).backgrounds)
        assertEquals(listOf(2), grouped.getValue(2).elements.map { it.imageId })
    }

    private fun background(imageId: Int, tagId: Int) = SelectedBackground(
        imageId = imageId,
        tagId = tagId,
        tagName = "mood $tagId",
        width = 2752,
        height = 1536,
        meanLuminance = 0.3,
        dominantColors = emptyList(),
        layout = BackgroundLayout(gridColumns = 0, gridRows = 0, cells = emptyList(), calmRegion = null, textTone = "light"),
    )

    private fun element(imageId: Int, tagId: Int) = SelectedElement(
        imageId = imageId,
        tagId = tagId,
        tagName = "motif $tagId",
        width = 1024,
        height = 1024,
        dominantColors = emptyList(),
        contentBox = NormalizedRect(0.1, 0.1, 0.8, 0.8),
    )
}
