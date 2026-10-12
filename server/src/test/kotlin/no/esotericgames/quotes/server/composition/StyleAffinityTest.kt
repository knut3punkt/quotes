package no.esotericgames.quotes.server.composition

import no.esotericgames.quotes.server.imagegen.ImageKind
import no.esotericgames.quotes.server.imagegen.StyledPromptRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StyleAffinityTest {

    private val known = setOf("a", "b", "c", "d")

    private fun affinity(groups: String) =
        StyleAffinity.parse("""{"version": "test-v1", "description": "test", "groups": [$groups]}""", known)

    private val sample = affinity(
        """
        {"id": "one", "description": "", "styles": ["a", "b", "c"]},
        {"id": "two", "description": "", "styles": ["a", "b"]}
        """,
    )

    @Test
    fun `same style scores highest`() {
        assertEquals(SAME_STYLE_FIT, sample.score("d", "d"))
        assertEquals(SAME_STYLE_FIT, sample.score("a", "a"))
    }

    @Test
    fun `scores count shared groups up to two`() {
        assertEquals(2, sample.score("a", "b"))
        assertEquals(1, sample.score("a", "c"))
        assertEquals(0, sample.score("a", "d"))
    }

    @Test
    fun `an unknown or missing style never fits`() {
        assertEquals(0, sample.score(null, "a"))
        assertEquals(0, sample.score("a", null))
        assertEquals(0, sample.score(null, null))
        assertEquals(0, sample.score("a", "unmapped"))
    }

    @Test
    fun `parsing refuses broken mappings`() {
        assertFailsWith<IllegalArgumentException> { affinity("") }
        assertFailsWith<IllegalArgumentException> { affinity("""{"id": "x", "description": "", "styles": ["nope"]}""") }
        assertFailsWith<IllegalArgumentException> { affinity("""{"id": "x", "description": "", "styles": []}""") }
        assertFailsWith<IllegalArgumentException> { affinity("""{"id": " ", "description": "", "styles": ["a"]}""") }
        assertFailsWith<IllegalArgumentException> { affinity("""{"id": "x", "description": "", "styles": ["a", "a"]}""") }
        assertFailsWith<IllegalArgumentException> {
            affinity("""{"id": "x", "description": "", "styles": ["a"]}, {"id": "x", "description": "", "styles": ["b"]}""")
        }
    }

    @Test
    fun `the bundled mapping puts every current style in at least two groups`() {
        val styles = ImageKind.entries.flatMap { StyledPromptRecipe.forKind(it).styleIds }.toSet()
        val bundled = StyleAffinity.BUNDLED
        styles.forEach { style ->
            assertTrue(bundled.groupsOf(style).size >= 2, "style '$style' is in groups ${bundled.groupsOf(style)}")
        }
    }

    @Test
    fun `the bundled mapping matches the documented examples`() {
        val bundled = StyleAffinity.BUNDLED
        assertEquals(2, bundled.score("neon-noir", "cyberpunk"))
        assertEquals(1, bundled.score("neon-noir", "synthwave-retrowave"))
        assertEquals(2, bundled.score("brutalist-scifi", "gothic-futurism"))
        assertEquals(0, bundled.score("solarpunk", "neon-noir"))
    }
}
