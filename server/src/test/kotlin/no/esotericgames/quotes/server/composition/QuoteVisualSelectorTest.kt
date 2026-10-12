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

    // "noir" and "neon" share a group; "pastel" shares none with either.
    private val testAffinity = StyleAffinity.parse(
        """{"version": "test-v1", "description": "test", "groups": [{"id": "night", "description": "", "styles": ["noir", "neon"]}]}""",
        setOf("noir", "neon", "pastel"),
    )

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
    fun `there is at most one element, from one of the motif tags`() {
        val elements = (1..6).flatMap { tagId -> listOf(element(imageId = tagId * 10, tagId), element(tagId * 10 + 1, tagId)) }
        repeat(100) { seed ->
            val visuals = selector(seed).select(QuoteVisualCandidates(emptyList(), elements))
            assertEquals(1, visuals.elements.size)
            assertTrue(visuals.elements.single() in elements)
            assertNull(visuals.styleFit)
        }
        val withBackground = selector(1).select(QuoteVisualCandidates(listOf(background(1, 10)), elements))
        assertEquals(1, withBackground.elements.size)
    }

    @Test
    fun `the motif tag whose style fits the background wins`() {
        val backgrounds = listOf(background(imageId = 1, tagId = 10, style = "noir"))
        val elements = listOf(
            element(imageId = 2, tagId = 20, style = "pastel"),
            element(imageId = 3, tagId = 21, style = "neon"),
            element(imageId = 4, tagId = 22, style = null),
        )
        repeat(50) { seed ->
            val visuals = selector(seed).select(QuoteVisualCandidates(backgrounds, elements))
            assertEquals(3, visuals.elements.single().imageId)
            assertEquals(1, visuals.styleFit)
        }
    }

    @Test
    fun `the background image that fits a motif best is chosen among the mood tag's images`() {
        val backgrounds = listOf(
            background(imageId = 1, tagId = 10, style = "pastel"),
            background(imageId = 2, tagId = 10, style = "noir"),
            background(imageId = 3, tagId = 10, style = null),
        )
        val elements = listOf(element(imageId = 4, tagId = 20, style = "noir"))
        repeat(50) { seed ->
            val visuals = selector(seed).select(QuoteVisualCandidates(backgrounds, elements))
            assertEquals(2, visuals.background!!.imageId)
            assertEquals(SAME_STYLE_FIT, visuals.styleFit)
        }
    }

    @Test
    fun `a motif tag's best-fitting image is used`() {
        val backgrounds = listOf(background(imageId = 1, tagId = 10, style = "noir"))
        val elements = listOf(element(imageId = 2, tagId = 20, style = null), element(imageId = 3, tagId = 20, style = "neon"))
        repeat(50) { seed ->
            assertEquals(3, selector(seed).select(QuoteVisualCandidates(backgrounds, elements)).elements.single().imageId)
        }
    }

    @Test
    fun `an unfitting pair is still shown when nothing fits, spread over the candidates`() {
        val backgrounds = listOf(background(imageId = 1, tagId = 10, style = "noir"))
        val elements = listOf(element(imageId = 2, tagId = 20, style = "pastel"), element(imageId = 3, tagId = 21, style = null))
        val picked = (0 until 100).map { seed ->
            val visuals = selector(seed).select(QuoteVisualCandidates(backgrounds, elements))
            assertEquals(0, visuals.styleFit)
            visuals.elements.single().imageId
        }
        assertEquals(setOf(2, 3), picked.toSet())
    }

    @Test
    fun `equally fitting motif tags share the draws`() {
        val backgrounds = listOf(background(imageId = 1, tagId = 10, style = "noir"))
        // Tag 20 has three fitting images, tag 21 one: each tag still gets about half.
        val elements = (2..4).map { element(imageId = it, tagId = 20, style = "neon") } +
            element(imageId = 5, tagId = 21, style = "neon")
        val tag21Share = (0 until 200).count { seed ->
            selector(seed).select(QuoteVisualCandidates(backgrounds, elements)).elements.single().tagId == 21
        } / 200.0
        assertTrue(tag21Share in 0.35..0.65, "tag 21 picked in ${tag21Share * 100}% of draws")
    }

    @Test
    fun `grouping keeps an image once per quote and splits backgrounds from elements`() {
        val rows = listOf(
            CandidateRow(quoteId = 1, excerptId = null, background(imageId = 1, tagId = 10)),
            CandidateRow(quoteId = 1, excerptId = null, background(imageId = 1, tagId = 10)),
            CandidateRow(quoteId = 1, excerptId = null, element(imageId = 2, tagId = 20)),
            CandidateRow(quoteId = 2, excerptId = null, element(imageId = 2, tagId = 20)),
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
            CandidateRow(quoteId = 1, excerptId = null, element(imageId = 1, tagId = 10)),
            CandidateRow(quoteId = 1, excerptId = 100, element(imageId = 2, tagId = 20)),
            CandidateRow(quoteId = 1, excerptId = 101, element(imageId = 3, tagId = 30)),
            CandidateRow(quoteId = 2, excerptId = 200, element(imageId = 4, tagId = 40)),
        )

        val grouped = groupVisualCandidates(rows, shownExcerpts = mapOf(1 to 100, 2 to null))

        assertEquals(listOf(1, 2), grouped.getValue(1).elements.map { it.imageId })
        assertEquals(listOf(4), grouped.getValue(2).elements.map { it.imageId })
    }

    private fun selector(seed: Int) = QuoteVisualSelector(Random(seed), testAffinity)

    private fun background(imageId: Int, tagId: Int, style: String? = null) = SelectedBackground(
        imageId = imageId,
        tagId = tagId,
        tagName = "mood $tagId",
        width = 2752,
        height = 1536,
        meanLuminance = 0.3,
        dominantColors = emptyList(),
        layout = BackgroundLayout(gridColumns = 0, gridRows = 0, cells = emptyList(), calmRegion = null, textTone = "light"),
        style = style,
        quoteLayout = null,
    )

    private fun element(imageId: Int, tagId: Int, style: String? = null) = SelectedElement(
        imageId = imageId,
        tagId = tagId,
        tagName = "motif $tagId",
        width = 1024,
        height = 1024,
        dominantColors = emptyList(),
        contentBox = NormalizedRect(0.1, 0.1, 0.8, 0.8),
        style = style,
    )
}
