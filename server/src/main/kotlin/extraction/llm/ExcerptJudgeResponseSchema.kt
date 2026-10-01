package no.esotericgames.quotes.server.extraction.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val MAX_SHORT_TEXT_LENGTH = 200
private const val MAX_REFERENCE_LENGTH = 80

/**
 * Schemas for the two judge calls (docs/features/quote-extraction.md's "Judge pass"). As with the
 * selector schema, property order is deliberate: the free-text observations come before the 1-5
 * levels, so the levels are generated after — and conditioned on — the model's own reading, rather
 * than being decided first and rationalized afterwards.
 *
 * Levels are anchored 1-5 rather than 0-100: small local models were observed to use a 0-100 scale
 * inconsistently (clustering everything at 85-95), while a handful of described levels is a much
 * easier judgment. The application maps levels to 0-100 for storage.
 */
val STANDALONE_JUDGE_JSON_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonArray("required") {
        listOf("whatItIsAbout", "unresolvedReferences", "insight", "standsAlone", "completeness", "quotability")
            .forEach { add(it) }
    }
    putJsonObject("properties") {
        putJsonObject("whatItIsAbout") {
            put("type", "string"); put("maxLength", MAX_SHORT_TEXT_LENGTH)
            put("description", "One sentence: what a first-time reader understands this quotation to be about.")
        }
        putJsonObject("unresolvedReferences") {
            put("type", "array")
            put(
                "description",
                "Words or phrases whose meaning depends on something the reader was not shown. Empty if none.",
            )
            putJsonObject("items") { put("type", "string"); put("maxLength", MAX_REFERENCE_LENGTH) }
        }
        putJsonObject("insight") {
            put("type", "string"); put("maxLength", MAX_SHORT_TEXT_LENGTH)
            put(
                "description",
                "The general insight, principle, or striking observation a reader takes away, in your own words. " +
                    "Empty string if there is none.",
            )
        }
        putLevel("standsAlone", "how fully a reader understands it without any surrounding text.")
        putLevel("completeness", "how fully it expresses a finished thought.")
        putLevel("quotability", "how worth quoting on its own it is, per the rubric in the instructions.")
    }
}

val FIDELITY_JUDGE_JSON_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonArray("required") { listOf("meaningInSource", "fidelity", "reason").forEach { add(it) } }
    putJsonObject("properties") {
        putJsonObject("meaningInSource") {
            put("type", "string"); put("maxLength", MAX_SHORT_TEXT_LENGTH)
            put("description", "One sentence: what the excerpt means within the full source passage.")
        }
        putLevel("fidelity", "how faithfully the excerpt, read alone, preserves the meaning it has in the source.")
        putJsonObject("reason") {
            put("type", "string"); put("maxLength", MAX_SHORT_TEXT_LENGTH)
            put("description", "A short diagnostic explanation, not shown to end users.")
        }
    }
}

private fun JsonObjectBuilder.putLevel(name: String, description: String) {
    putJsonObject(name) {
        put("type", "integer"); put("minimum", 1); put("maximum", 5)
        put("description", "Integer level from 1 (worst) to 5 (best): $description")
    }
}

val STANDALONE_JUDGE_RESPONSE_FORMAT: JsonObject = responseFormat("excerpt_standalone_judgment", STANDALONE_JUDGE_JSON_SCHEMA)
val FIDELITY_JUDGE_RESPONSE_FORMAT: JsonObject = responseFormat("excerpt_fidelity_judgment", FIDELITY_JUDGE_JSON_SCHEMA)

private fun responseFormat(name: String, schema: JsonObject): JsonObject = buildJsonObject {
    put("type", "json_schema")
    putJsonObject("json_schema") {
        put("name", name)
        put("strict", true)
        put("schema", schema)
    }
}

@Serializable
data class StandaloneVerdictDto(
    val whatItIsAbout: String = "",
    val unresolvedReferences: List<String> = emptyList(),
    val insight: String = "",
    val standsAlone: Int,
    val completeness: Int,
    val quotability: Int,
) {
    fun toVerdict() = StandaloneVerdict(whatItIsAbout, unresolvedReferences, insight, standsAlone, completeness, quotability)
}

@Serializable
data class FidelityVerdictDto(
    val meaningInSource: String = "",
    val fidelity: Int,
    val reason: String = "",
) {
    fun toVerdict() = FidelityVerdict(meaningInSource, fidelity, reason)
}
