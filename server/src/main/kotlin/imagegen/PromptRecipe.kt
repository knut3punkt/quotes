package no.esotericgames.quotes.server.imagegen

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

private val PLACEHOLDER = Regex("""\{\{([a-z][a-zA-Z0-9]*)}}""")
private const val TAG_PLACEHOLDER = "tag"

@Serializable
private data class PromptRecipeDefinition(
    val version: String,
    val template: String,
    val slots: Map<String, List<String>>,
    val negativePrompt: String,
)

/** A finished image prompt, plus which word was picked for each slot (stored for provenance). */
data class ComposedPrompt(
    val prompt: String,
    val negativePrompt: String,
    val ingredients: Map<String, String>,
)

/**
 * A versioned image-prompt recipe under `resources/image-prompts/`: a template with `{{slot}}`
 * placeholders, a list of interchangeable phrases per slot, and a negative prompt. `{{tag}}` is reserved
 * for the tag name. [compose] picks one phrase per slot with a [Random] seeded by the image seed, so the
 * same seed always gives the same prompt, and a batch of tags still gets varied images.
 *
 * When a recipe changes, add a new resource file, keep the old one, and point [forKind] at the new
 * version; each stored image records the recipe version it was generated with.
 */
class PromptRecipe private constructor(private val definition: PromptRecipeDefinition) {

    val version: String get() = definition.version

    fun compose(tagName: String, seed: Long): ComposedPrompt {
        val random = Random(seed)
        val ingredients = linkedMapOf<String, String>()
        // Slots are picked in template order, so the result doesn't depend on the JSON key order.
        placeholdersOf(definition.template).filter { it != TAG_PLACEHOLDER }.distinct().forEach { slot ->
            val options = definition.slots.getValue(slot)
            ingredients[slot] = options[random.nextInt(options.size)]
        }
        val prompt = PLACEHOLDER.replace(definition.template) { match ->
            val name = match.groupValues[1]
            if (name == TAG_PLACEHOLDER) tagName else ingredients.getValue(name)
        }
        return ComposedPrompt(prompt, definition.negativePrompt, ingredients)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        private val BUNDLED = mapOf(
            ImageKind.BACKGROUND to "/image-prompts/background-v1.json",
            ImageKind.ELEMENT to "/image-prompts/motif-v1.json",
        )

        fun forKind(kind: ImageKind): PromptRecipe {
            val resourcePath = BUNDLED.getValue(kind)
            val stream = PromptRecipe::class.java.getResourceAsStream(resourcePath)
                ?: error("bundled resource not found: $resourcePath")
            return parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        /** Parses and validates a recipe: every placeholder needs a non-empty slot, and `{{tag}}` must appear. */
        fun parse(recipeJson: String): PromptRecipe {
            val definition = json.decodeFromString(PromptRecipeDefinition.serializer(), recipeJson)
            val placeholders = placeholdersOf(definition.template)
            require(TAG_PLACEHOLDER in placeholders) { "recipe ${definition.version} never uses {{$TAG_PLACEHOLDER}}" }
            require(TAG_PLACEHOLDER !in definition.slots) { "recipe ${definition.version} defines a reserved slot '$TAG_PLACEHOLDER'" }
            placeholders.filter { it != TAG_PLACEHOLDER }.forEach { slot ->
                val options = definition.slots[slot]
                require(!options.isNullOrEmpty()) { "recipe ${definition.version} has no phrases for {{$slot}}" }
                require(options.none { it.isBlank() }) { "recipe ${definition.version} has a blank phrase in {{$slot}}" }
            }
            return PromptRecipe(definition)
        }

        private fun placeholdersOf(template: String): List<String> =
            PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toList()
    }
}
