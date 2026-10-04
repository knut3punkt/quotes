package no.esotericgames.quotes.server.imagegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PromptRecipeTest {

    private val recipeJson = """
        {
          "version": "test-v1",
          "template": "A {{medium}} picture of {{tag}} in {{light}} light, {{medium}} style.",
          "slots": { "medium": ["ink", "oil", "pastel"], "light": ["dim", "bright"] },
          "negativePrompt": "text"
        }
    """.trimIndent()

    @Test
    fun `same seed gives the same prompt, and every placeholder is filled`() {
        val recipe = PromptRecipe.parse(recipeJson)

        val first = recipe.compose("lighthouse", seed = 42)
        val second = recipe.compose("lighthouse", seed = 42)

        assertEquals(first, second)
        assertFalse("{{" in first.prompt)
        assertTrue("lighthouse" in first.prompt)
        assertEquals(setOf("medium", "light"), first.ingredients.keys)
        // A slot used twice gets the same phrase both times.
        val medium = first.ingredients.getValue("medium")
        assertEquals(2, Regex(Regex.escape(medium)).findAll(first.prompt).count())
        assertEquals("text", first.negativePrompt)
    }

    @Test
    fun `different seeds vary the prompt`() {
        val recipe = PromptRecipe.parse(recipeJson)
        val prompts = (0L until 20L).map { recipe.compose("lighthouse", it).prompt }.toSet()
        assertNotEquals(1, prompts.size)
    }

    @Test
    fun `rejects a recipe with a placeholder that has no phrases`() {
        val broken = recipeJson.replace("\"light\": [\"dim\", \"bright\"]", "\"light\": []")
        assertFailsWith<IllegalArgumentException> { PromptRecipe.parse(broken) }
    }

    @Test
    fun `rejects a recipe that never uses the tag`() {
        val broken = recipeJson.replace("{{tag}}", "something")
        assertFailsWith<IllegalArgumentException> { PromptRecipe.parse(broken) }
    }

    @Test
    fun `bundled recipes load and keep the transparency phrasing for elements`() {
        val background = PromptRecipe.forKind(ImageKind.BACKGROUND).compose("serene", 7)
        val element = PromptRecipe.forKind(ImageKind.ELEMENT).compose("lantern", 7)

        assertTrue("serene" in background.prompt)
        assertTrue(element.prompt.startsWith("This is an RGBA image with transparency."))
        assertTrue("background is transparent" in element.prompt)
    }
}
