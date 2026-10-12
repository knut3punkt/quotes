package no.esotericgames.quotes.server.composition

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.imagegen.ImageKind
import no.esotericgames.quotes.server.imagegen.StyledPromptRecipe

/** The best fit: a background and a motif in the same style. */
const val SAME_STYLE_FIT = 3

@Serializable
private data class AffinityGroup(val id: String, val description: String, val styles: List<String>)

@Serializable
private data class StyleAffinityDefinition(val version: String, val description: String, val groups: List<AffinityGroup>)

/**
 * How well a background style and a motif style look together (docs/features/quote-composition.md,
 * "Style affinity"), read from a versioned resource under `resources/composition/`. Each style belongs to
 * overlapping groups of shared visual traits, and [score] counts what two styles share.
 */
class StyleAffinity private constructor(val version: String, private val groupsByStyle: Map<String, Set<String>>) {

    /** The groups [style] belongs to; empty for a style the mapping doesn't know. */
    fun groupsOf(style: String): Set<String> = groupsByStyle[style].orEmpty()

    /**
     * [SAME_STYLE_FIT] for the same style, 2 for two or more shared groups, 1 for one, and 0 for none or
     * when either style is unknown (images from recipes without style profiles).
     */
    fun score(backgroundStyle: String?, motifStyle: String?): Int {
        if (backgroundStyle == null || motifStyle == null) return 0
        if (backgroundStyle == motifStyle) return SAME_STYLE_FIT
        return minOf(2, (groupsOf(backgroundStyle) intersect groupsOf(motifStyle)).size)
    }

    companion object {
        private const val RESOURCE_PATH = "/composition/style-affinity-v1.json"
        private val json = Json { ignoreUnknownKeys = false }

        /** The bundled mapping, checked against the style profiles of the current image recipes. */
        val BUNDLED: StyleAffinity by lazy {
            val stream = StyleAffinity::class.java.getResourceAsStream(RESOURCE_PATH)
                ?: error("bundled resource not found: $RESOURCE_PATH")
            val knownStyles = ImageKind.entries.flatMapTo(mutableSetOf()) { StyledPromptRecipe.forKind(it).styleIds }
            parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() }, knownStyles)
        }

        /**
         * Parses and validates a mapping: group ids are unique and not blank, every group has styles, no group
         * lists a style twice, and every style is one of [knownStyles].
         */
        fun parse(affinityJson: String, knownStyles: Set<String>): StyleAffinity {
            val definition = json.decodeFromString(StyleAffinityDefinition.serializer(), affinityJson)
            val version = definition.version
            require(definition.groups.isNotEmpty()) { "style affinity $version has no groups" }
            definition.groups.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
                throw IllegalArgumentException("style affinity $version has duplicate group id '${it.key}'")
            }
            definition.groups.forEach { group ->
                require(group.id.isNotBlank()) { "style affinity $version has a group with a blank id" }
                require(group.styles.isNotEmpty()) { "style affinity $version: group '${group.id}' has no styles" }
                require(group.styles.toSet().size == group.styles.size) {
                    "style affinity $version: group '${group.id}' lists a style twice"
                }
                (group.styles - knownStyles).firstOrNull()?.let {
                    throw IllegalArgumentException("style affinity $version: group '${group.id}' has unknown style '$it'")
                }
            }
            val groupsByStyle = definition.groups
                .flatMap { group -> group.styles.map { it to group.id } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, groups) -> groups.toSet() }
            return StyleAffinity(version, groupsByStyle)
        }
    }
}
