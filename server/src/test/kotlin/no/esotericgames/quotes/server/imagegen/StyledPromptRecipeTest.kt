package no.esotericgames.quotes.server.imagegen

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StyledPromptRecipeTest {

    private val recipeJson = """
        {
          "version": "test-styled-v1",
          "description": "test",
          "inputs": { "tag": "a mood" },
          "template": "{{medium}} for a {{tag}} mood. {{look}}. {{texture}}. {{composition}} {{quoteInstruction}}",
          "quoteLayouts": [
            { "id": "left", "weight": 1.0, "instruction": "Quiet left.", "composition": "Busy right." },
            { "id": "overlay", "weight": 1.0, "instruction": "No quiet area.", "composition": "Edge to edge." }
          ],
          "styleProfiles": [
            { "id": "heavy", "weight": 50.0, "mediums": ["Heavy medium A", "Heavy medium B"], "looks": ["Heavy look"], "textures": ["Heavy texture"] },
            { "id": "light", "weight": 1.0, "mediums": ["Light medium"], "looks": ["Light look A", "Light look B"], "textures": ["Light texture"] },
            { "id": "other", "weight": 1.0, "mediums": ["Other medium"], "looks": ["Other look"], "textures": ["Other texture A", "Other texture B"] }
          ],
          "globalInstructions": ["Not sent to the model."],
          "negativePrompt": "text",
          "selectionRules": { "crossStyleMixing": false }
        }
    """.trimIndent()

    @Test
    fun `same seed and style give the same prompt, drawn only from that style`() {
        val recipe = StyledPromptRecipe.parse(recipeJson)

        val first = recipe.compose("serene", "light", seed = 42)
        val second = recipe.compose("serene", "light", seed = 42)

        assertEquals(first, second)
        assertFalse("{{" in first.prompt)
        assertTrue("serene" in first.prompt)
        assertEquals("text", first.negativePrompt)
        assertEquals("test-styled-v1", first.recipeVersion)
        assertEquals(setOf("style", "medium", "look", "texture", "quoteLayout"), first.ingredients.keys)
        assertEquals("light", first.ingredients["style"])
        assertEquals("Light medium", first.ingredients["medium"])
        assertTrue(first.ingredients.getValue("look") in setOf("Light look A", "Light look B"))
        assertFalse("Heavy" in first.prompt || "Other" in first.prompt)
        assertFalse("Not sent to the model" in first.prompt)
    }

    @Test
    fun `the quote layout supplies both its instruction and its composition`() {
        val recipe = StyledPromptRecipe.parse(recipeJson)
        val prompts = (0L until 40L).map { recipe.compose("serene", "other", it) }

        val layouts = prompts.map { it.ingredients.getValue("quoteLayout") }.toSet()
        assertEquals(setOf("left", "overlay"), layouts)
        prompts.filter { it.ingredients["quoteLayout"] == "overlay" }.forEach {
            assertTrue("Edge to edge. No quiet area." in it.prompt)
            assertFalse("Quiet left." in it.prompt)
        }
        prompts.filter { it.ingredients["quoteLayout"] == "left" }.forEach {
            assertTrue("Busy right. Quiet left." in it.prompt)
        }
    }

    @Test
    fun `pickStyles returns distinct styles, honours exclusions, and favours heavy weights`() {
        val recipe = StyledPromptRecipe.parse(recipeJson)
        val random = Random(1)

        repeat(20) {
            val picked = recipe.pickStyles(3, emptySet(), random)
            assertEquals(3, picked.size)
            assertEquals(3, picked.toSet().size)
        }
        assertEquals(listOf("other"), recipe.pickStyles(3, setOf("heavy", "light"), random))
        assertEquals(emptyList(), recipe.pickStyles(2, setOf("heavy", "light", "other"), random))

        val firstPicks = (0 until 200).map { recipe.pickStyles(1, emptySet(), random).single() }
        assertTrue(firstPicks.count { it == "heavy" } > 150)
    }

    @Test
    fun `rejects malformed recipes`() {
        val broken = listOf(
            recipeJson.replace("{{tag}}", "something"),
            recipeJson.replace("{{texture}}", "{{palette}}"),
            recipeJson.replace("\"mediums\": [\"Light medium\"]", "\"mediums\": []"),
            recipeJson.replace("\"weight\": 50.0", "\"weight\": 0.0"),
            recipeJson.replace("\"id\": \"other\"", "\"id\": \"light\""),
            recipeJson.replace("\"looks\": [\"Heavy look\"]", "\"looks\": [\" \"]"),
            // inputs must name exactly one subject placeholder
            recipeJson.replace("{ \"tag\": \"a mood\" }", "{}"),
            recipeJson.replace("{ \"tag\": \"a mood\" }", "{ \"tag\": \"a mood\", \"other\": \"x\" }"),
            // a style list the template never uses
            recipeJson.replace(" {{texture}}.", ""),
            // styles with differing lists
            recipeJson.replace("\"textures\": [\"Light texture\"]", "\"palettes\": [\"Light texture\"]"),
            // a style field that is not a plural phrase list
            recipeJson.replace("\"textures\": [\"Light texture\"]", "\"textures\": \"Light texture\""),
            recipeJson.replace("\"textures\": [\"Light texture\"]", "\"textures\": [\"Light texture\"], \"note\": [\"x\"]"),
        )
        broken.forEach { json -> assertFailsWith<IllegalArgumentException>(json) { StyledPromptRecipe.parse(json) } }
    }

    private val motifJson = """
        {
          "version": "test-motif-v1",
          "description": "test",
          "inputs": { "motif": "a symbolic subject" },
          "template": "RGBA. {{motif}}. {{medium}}. {{treatment}}. {{edgeTreatment}} Transparent.",
          "styleProfiles": [
            { "id": "ink", "weight": 1.0, "mediums": ["Ink medium"], "treatments": ["Ink treatment"], "edgeTreatments": ["Ink edge."] },
            { "id": "glass", "weight": 2.0, "mediums": ["Glass medium"], "treatments": ["Glass treatment"], "edgeTreatments": ["Glass edge."] }
          ],
          "globalInstructions": ["Not sent to the model."],
          "negativePrompt": "text",
          "selectionRules": { "crossStyleMixing": false },
          "assetComposition": { "canvas": "square" }
        }
    """.trimIndent()

    @Test
    fun `a recipe without quote layouts fills its own subject and phrase lists`() {
        val composed = StyledPromptRecipe.parse(motifJson).compose("lantern", "ink", seed = 3)

        assertEquals("RGBA. lantern. Ink medium. Ink treatment. Ink edge. Transparent.", composed.prompt)
        assertEquals(
            mapOf("style" to "ink", "medium" to "Ink medium", "treatment" to "Ink treatment", "edgeTreatment" to "Ink edge."),
            composed.ingredients,
        )
        // Layout placeholders need quote layouts.
        assertFailsWith<IllegalArgumentException> {
            StyledPromptRecipe.parse(motifJson.replace("Transparent.", "{{composition}} Transparent."))
        }
    }

    @Test
    fun `bundled background recipe loads, composes every style, and keeps its picks stable`() {
        val recipe = StyledPromptRecipe.forKind(ImageKind.BACKGROUND)
        assertEquals("background-v4", recipe.version)
        recipe.styleIds.forEach { style ->
            val composed = recipe.compose("serene", style, 7)
            assertFalse("{{" in composed.prompt)
            assertTrue("serene mood" in composed.prompt)
            assertEquals(style, composed.ingredients["style"])
        }
        // Stored backgrounds can be reproduced from their seed and style: these picks predate the motif
        // recipe sharing this class, and must not change.
        val seven = recipe.compose("serene", "dark-surrealism", 7).ingredients
        assertEquals("A dark digital matte artwork", seven["medium"])
        assertTrue(seven.getValue("look").startsWith("Dark surrealism using dream logic"))
        assertTrue(seven.getValue("texture").startsWith("Atmospheric haze"))
        assertEquals("top", seven["quoteLayout"])
        val other = recipe.compose("serene", "dark-surrealism", 999).ingredients
        assertEquals("A surreal fine-art photographic composition", other["medium"])
        assertTrue(other.getValue("texture").startsWith("Soft fog"))
        assertEquals("top", other["quoteLayout"])
        assertEquals("center", recipe.compose("serene", "dark-surrealism", 12345).ingredients["quoteLayout"])
    }

    @Test
    fun `bundled motif recipe keeps the transparency phrasing for every style`() {
        val recipe = StyledPromptRecipe.forKind(ImageKind.ELEMENT)
        assertEquals("motif-v2", recipe.version)
        recipe.styleIds.forEach { style ->
            val composed = recipe.compose("lantern", style, 7)
            assertTrue(composed.prompt.startsWith("This is an RGBA image with transparency."))
            assertTrue(composed.prompt.endsWith("The image has alpha channel and the background is transparent."))
            assertTrue("symbolic motif: lantern." in composed.prompt)
            assertFalse("{{" in composed.prompt)
            assertEquals(setOf("style", "medium", "treatment", "edgeTreatment"), composed.ingredients.keys)
        }
    }
}
