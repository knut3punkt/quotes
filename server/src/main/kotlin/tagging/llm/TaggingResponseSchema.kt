package no.esotericgames.quotes.server.tagging.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Hard ceilings for the schema. The tighter, configurable per-subject caps are applied by
// TaggingValidator (see TaggingPolicy); these only stop a runaway response.
const val MAX_CONCEPTS = 12
const val MAX_MOODS = 3
const val MAX_MOTIFS = 5
const val MAX_TAG_NAME_LENGTH = 48

/**
 * Schema-constrained `response_format` for llama-server's chat-completions endpoint. Kept as a Kotlin
 * object, like the interpretation schema, so it can never drift from [TaggingResponseDto]. Name
 * normalization, word limits, duplicate detection and relevance clamping are validated
 * deterministically in [no.esotericgames.quotes.server.tagging.TaggingValidator].
 */
val TAGGING_JSON_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonArray("required") { listOf("concepts", "moods", "motifs").forEach { add(it) } }
    putJsonObject("properties") {
        putJsonObject("concepts") {
            tagArray(
                maxItems = MAX_CONCEPTS,
                nameDescription = "A short noun phrase (1-4 words) naming an idea the quotation is about. " +
                    "Prefer an existing tag when it means the same thing.",
                withBreadth = true,
            )
        }
        putJsonObject("moods") {
            tagArray(
                maxItems = MAX_MOODS,
                nameDescription = "One adjective (or two words at most) for the emotional register a reader feels.",
                withBreadth = false,
            )
        }
        putJsonObject("motifs") {
            tagArray(
                maxItems = MAX_MOTIFS,
                nameDescription = "A concrete, drawable thing the quotation evokes, as a singular noun (1-3 words).",
                withBreadth = false,
            )
        }
    }
}

private fun JsonObjectBuilder.tagArray(maxItems: Int, nameDescription: String, withBreadth: Boolean) {
    put("type", "array")
    put("maxItems", maxItems)
    putJsonObject("items") {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") {
            add("name")
            if (withBreadth) add("breadth")
            add("relevance")
            add("basis")
        }
        putJsonObject("properties") {
            putJsonObject("name") {
                put("type", "string")
                put("maxLength", MAX_TAG_NAME_LENGTH)
                put("description", nameDescription)
            }
            if (withBreadth) {
                putJsonObject("breadth") {
                    put("type", "string")
                    putJsonArray("enum") { add("broad"); add("specific") }
                    put("description", "broad: a theme many quotations share. specific: a granular idea.")
                }
            }
            putJsonObject("relevance") {
                put("type", "integer"); put("minimum", 1); put("maximum", 3)
                put("description", "3 = central to the quotation, 2 = significant, 1 = peripheral.")
            }
            putJsonObject("basis") {
                put("type", "string")
                putJsonArray("enum") { add("text"); add("interpretation") }
                put(
                    "description",
                    "text: supported by the quotation's own words. interpretation: comes from one of the supplied interpretive readings.",
                )
            }
        }
    }
}

val TAGGING_RESPONSE_FORMAT: JsonObject = buildJsonObject {
    put("type", "json_schema")
    putJsonObject("json_schema") {
        put("name", "quote_tags")
        put("strict", true)
        put("schema", TAGGING_JSON_SCHEMA)
    }
}

@Serializable
data class TaggingResponseDto(
    val concepts: List<TagCandidateDto> = emptyList(),
    val moods: List<TagCandidateDto> = emptyList(),
    val motifs: List<TagCandidateDto> = emptyList(),
) {
    fun toRawTagging() = RawTagging(
        concepts = concepts.map { it.toRawCandidate() },
        moods = moods.map { it.toRawCandidate() },
        motifs = motifs.map { it.toRawCandidate() },
    )
}

@Serializable
data class TagCandidateDto(
    val name: String,
    val breadth: String? = null,
    val relevance: Int,
    val basis: String,
) {
    fun toRawCandidate() = RawTagCandidate(name = name, breadth = breadth, relevance = relevance, basis = basis)
}
