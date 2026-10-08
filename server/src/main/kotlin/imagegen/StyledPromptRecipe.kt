package no.esotericgames.quotes.server.imagegen

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.random.Random

private val PLACEHOLDER = Regex("""\{\{([a-z][a-zA-Z0-9]*)}}""")
private const val STYLE_ID_FIELD = "id"
private const val STYLE_WEIGHT_FIELD = "weight"
private const val COMPOSITION_PLACEHOLDER = "composition"
private const val QUOTE_INSTRUCTION_PLACEHOLDER = "quoteInstruction"

/** A finished image prompt, plus each choice made for it and the recipe version (stored for provenance). */
data class ComposedPrompt(
    val prompt: String,
    val negativePrompt: String,
    val ingredients: Map<String, String>,
    val recipeVersion: String,
)

@Serializable
private data class QuoteLayout(
    val id: String,
    val weight: Double,
    val instruction: String,
    val composition: String,
)

@Serializable
private data class StyledPromptRecipeDefinition(
    val version: String,
    val description: String,
    // Exactly one entry: the placeholder that takes the tag name, and what it means.
    val inputs: Map<String, String>,
    val template: String,
    val quoteLayouts: List<QuoteLayout> = emptyList(),
    // Decoded by hand, since each recipe names its own phrase lists; see [StyleProfile].
    val styleProfiles: List<JsonObject>,
    // Guidance for whoever edits the recipe; deliberately not sent to the image model (see tag-images.md).
    val globalInstructions: List<String>,
    val negativePrompt: String,
    // Documents the rules that [StyledPromptRecipe] enforces in code; not read otherwise.
    val selectionRules: JsonObject,
    // Guidance on the generated asset's framing; not read.
    val assetComposition: JsonObject? = null,
)

/** A style profile; [phrases] maps each placeholder (`medium`, `look`, ...) to the phrases it may take. */
private data class StyleProfile(val id: String, val weight: Double, val phrases: Map<String, List<String>>)

/**
 * A versioned image-prompt recipe under `resources/image-prompts/` built from weighted style profiles and,
 * for backgrounds, weighted quote layouts (docs/features/tag-images.md).
 *
 * Each style profile lists phrases under plural names (`mediums`, `looks`, `edgeTreatments`, ...), and the
 * template uses the singular as a placeholder (`{{medium}}`). The single key of `inputs` is the placeholder
 * that takes the tag name. A tag's styles are chosen up front with [pickStyles], so a tag's images differ in
 * style. [compose] then fills in one image for one style: one phrase per list, only from that style, picked
 * uniformly, and a quote layout picked by weight, all with a [Random] seeded by the image seed, so the seed
 * and style reproduce the prompt.
 */
class StyledPromptRecipe private constructor(
    private val definition: StyledPromptRecipeDefinition,
    private val subjectPlaceholder: String,
    private val styles: List<StyleProfile>,
    // Style phrase lists in template order, so picks don't depend on JSON key order.
    private val phrasePlaceholders: List<String>,
) {

    val version: String get() = definition.version

    val styleIds: List<String> get() = styles.map { it.id }

    /**
     * Picks up to [count] distinct styles by weight, without replacement, leaving out [exclude]. Returns fewer
     * when not enough styles remain.
     */
    fun pickStyles(count: Int, exclude: Set<String>, random: Random): List<String> {
        val remaining = styles.filterTo(mutableListOf()) { it.id !in exclude }
        return buildList {
            repeat(minOf(count, remaining.size)) {
                val picked = weightedPick(remaining, StyleProfile::weight, random)
                remaining.remove(picked)
                add(picked.id)
            }
        }
    }

    fun compose(tagName: String, styleId: String, seed: Long): ComposedPrompt {
        val style = styles.firstOrNull { it.id == styleId }
            ?: throw IllegalArgumentException("recipe ${definition.version} has no style '$styleId'")
        val random = Random(seed)
        val ingredients = linkedMapOf("style" to style.id)
        phrasePlaceholders.forEach { placeholder ->
            val options = style.phrases.getValue(placeholder)
            ingredients[placeholder] = options[random.nextInt(options.size)]
        }
        val values = HashMap<String, String>(ingredients)
        values[subjectPlaceholder] = tagName
        if (definition.quoteLayouts.isNotEmpty()) {
            val layout = weightedPick(definition.quoteLayouts, QuoteLayout::weight, random)
            ingredients["quoteLayout"] = layout.id
            values[COMPOSITION_PLACEHOLDER] = layout.composition
            values[QUOTE_INSTRUCTION_PLACEHOLDER] = layout.instruction
        }
        val prompt = PLACEHOLDER.replace(definition.template) { match -> values.getValue(match.groupValues[1]) }
        return ComposedPrompt(prompt, definition.negativePrompt, ingredients, definition.version)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        private val BUNDLED = mapOf(
            ImageKind.BACKGROUND to "/image-prompts/background-v4.json",
            ImageKind.ELEMENT to "/image-prompts/motif-v2.json",
        )

        fun forKind(kind: ImageKind): StyledPromptRecipe {
            val resourcePath = BUNDLED.getValue(kind)
            val stream = StyledPromptRecipe::class.java.getResourceAsStream(resourcePath)
                ?: error("bundled resource not found: $resourcePath")
            return parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        /**
         * Parses and validates a recipe: `inputs` names exactly one subject placeholder, which the template
         * uses; every other placeholder is a style phrase list or, with quote layouts, a layout field; every
         * style has the same phrase lists, all used, non-empty and without blanks; ids are unique and weights
         * positive.
         */
        fun parse(recipeJson: String): StyledPromptRecipe {
            val definition = json.decodeFromString(StyledPromptRecipeDefinition.serializer(), recipeJson)
            val version = definition.version
            require(definition.inputs.size == 1) { "recipe $version must name exactly one input, the tag placeholder" }
            val subject = definition.inputs.keys.single()

            require(definition.styleProfiles.isNotEmpty()) { "recipe $version has no style profiles" }
            val styles = definition.styleProfiles.map { parseStyle(version, it) }
            requireUniqueIds(version, "style profile", styles.map { it.id })
            val phraseNames = styles.first().phrases.keys
            styles.forEach { style ->
                require(style.phrases.keys == phraseNames) {
                    "recipe $version: style '${style.id}' has phrase lists ${style.phrases.keys}, expected $phraseNames"
                }
            }
            require(subject !in phraseNames) { "recipe $version uses '$subject' both as input and as a style list" }

            requireUniqueIds(version, "quote layout", definition.quoteLayouts.map { it.id })
            definition.quoteLayouts.forEach { layout ->
                requirePositiveWeight(version, layout.id, layout.weight)
                require(layout.instruction.isNotBlank() && layout.composition.isNotBlank()) {
                    "recipe $version has a blank instruction or composition in quote layout '${layout.id}'"
                }
            }
            val layoutPlaceholders =
                if (definition.quoteLayouts.isEmpty()) emptySet() else setOf(COMPOSITION_PLACEHOLDER, QUOTE_INSTRUCTION_PLACEHOLDER)
            require(phraseNames.none { it in layoutPlaceholders }) { "recipe $version has a style list named like a layout field" }

            val placeholders = PLACEHOLDER.findAll(definition.template).map { it.groupValues[1] }.toList()
            require(subject in placeholders) { "recipe $version never uses {{$subject}}" }
            (placeholders.toSet() - subject - phraseNames - layoutPlaceholders).firstOrNull()?.let {
                throw IllegalArgumentException("recipe $version uses unknown placeholder {{$it}}")
            }
            (phraseNames - placeholders.toSet()).firstOrNull()?.let {
                throw IllegalArgumentException("recipe $version never uses its style list {{$it}}")
            }
            (layoutPlaceholders - placeholders.toSet()).firstOrNull()?.let {
                throw IllegalArgumentException("recipe $version has quote layouts but never uses {{$it}}")
            }
            val phrasePlaceholders = placeholders.filter { it in phraseNames }.distinct()
            return StyledPromptRecipe(definition, subject, styles, phrasePlaceholders)
        }

        /** Reads one style profile: `id`, `weight`, and plural-named phrase lists keyed by their singular. */
        private fun parseStyle(version: String, profile: JsonObject): StyleProfile {
            val id = (profile[STYLE_ID_FIELD] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("recipe $version has a style profile without a string id")
            val weight = (profile[STYLE_WEIGHT_FIELD] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
                ?: throw IllegalArgumentException("recipe $version: style '$id' has no numeric weight")
            requirePositiveWeight(version, id, weight)
            val phrases = profile.filterKeys { it != STYLE_ID_FIELD && it != STYLE_WEIGHT_FIELD }
                .map { (field, value) ->
                    require(field.length > 1 && field.endsWith("s")) {
                        "recipe $version: style '$id' field '$field' should be a plural phrase list, like 'mediums'"
                    }
                    val list = (value as? JsonArray)?.map { entry ->
                        (entry as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                            ?: throw IllegalArgumentException("recipe $version: style '$id' has a non-string entry in $field")
                    } ?: throw IllegalArgumentException("recipe $version: style '$id' field '$field' is not a list")
                    require(list.isNotEmpty()) { "recipe $version has no $field in style '$id'" }
                    require(list.none { it.isBlank() }) { "recipe $version has a blank entry in $field of style '$id'" }
                    field.dropLast(1) to list
                }
                .toMap()
            require(phrases.isNotEmpty()) { "recipe $version: style '$id' has no phrase lists" }
            return StyleProfile(id, weight, phrases)
        }

        private fun requireUniqueIds(version: String, what: String, ids: List<String>) {
            require(ids.none { it.isBlank() }) { "recipe $version has a $what with a blank id" }
            ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
                throw IllegalArgumentException("recipe $version has duplicate $what id '${it.key}'")
            }
        }

        private fun requirePositiveWeight(version: String, id: String, weight: Double) {
            require(weight.isFinite() && weight > 0.0) { "recipe $version gives '$id' a weight that isn't positive: $weight" }
        }

        /** Picks one item with probability proportional to its weight. Weights are validated as positive. */
        private fun <T> weightedPick(items: List<T>, weightOf: (T) -> Double, random: Random): T {
            val total = items.sumOf(weightOf)
            var target = random.nextDouble(total)
            for (item in items) {
                target -= weightOf(item)
                if (target < 0.0) return item
            }
            // Floating-point rounding can leave a sliver past the last item.
            return items.last()
        }
    }
}
