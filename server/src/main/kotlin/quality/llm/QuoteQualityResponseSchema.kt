package no.esotericgames.quotes.server.quality.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import no.esotericgames.quotes.server.quality.QualityProblem
import no.esotericgames.quotes.server.quality.QualityVerdict

private const val MAX_SHORT_TEXT_LENGTH = 200
private const val MAX_ASSESSMENT_LENGTH = 300
private const val MAX_REFERENCE_LENGTH = 80

/**
 * Schema for one quality assessment (prompts/quote-quality-v1.md). As in the extraction judge's
 * schemas, property order is the generation order: the reading, references, insight and problems
 * come before the levels and the verdict, so the verdict is conditioned on the model's own reading.
 * External runners receive this schema verbatim from the admin API.
 */
val QUALITY_JSON_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonArray("required") {
        listOf(
            "reading", "unresolvedReferences", "insight", "problems",
            "standsAlone", "completeness", "substance", "expression", "assessment", "verdict",
        ).forEach { add(it) }
    }
    putJsonObject("properties") {
        putJsonObject("reading") { put("type", "string"); put("maxLength", MAX_SHORT_TEXT_LENGTH) }
        putJsonObject("unresolvedReferences") {
            put("type", "array")
            putJsonObject("items") { put("type", "string"); put("maxLength", MAX_REFERENCE_LENGTH) }
        }
        putJsonObject("insight") { put("type", "string"); put("maxLength", MAX_SHORT_TEXT_LENGTH) }
        putJsonObject("problems") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "string")
                putJsonArray("enum") { QualityProblem.entries.forEach { add(it.name) } }
            }
        }
        putLevel("standsAlone")
        putLevel("completeness")
        putLevel("substance")
        putLevel("expression")
        putJsonObject("assessment") { put("type", "string"); put("maxLength", MAX_ASSESSMENT_LENGTH) }
        putJsonObject("verdict") {
            put("type", "string")
            putJsonArray("enum") { QualityVerdict.entries.forEach { add(it.dbValue) } }
        }
    }
}

private fun JsonObjectBuilder.putLevel(name: String) {
    putJsonObject(name) { put("type", "integer"); put("minimum", 1); put("maximum", 5) }
}

val QUALITY_RESPONSE_FORMAT: JsonObject = buildJsonObject {
    put("type", "json_schema")
    putJsonObject("json_schema") {
        put("name", "quote_quality_assessment")
        put("strict", true)
        put("schema", QUALITY_JSON_SCHEMA)
    }
}

/** Decoded as plain strings and ints: [no.esotericgames.quotes.server.quality.QualityValidator] checks the values. */
@Serializable
data class QualityResponseDto(
    val reading: String = "",
    val unresolvedReferences: List<String> = emptyList(),
    val insight: String = "",
    val problems: List<String> = emptyList(),
    val standsAlone: Int,
    val completeness: Int,
    val substance: Int,
    val expression: Int,
    val assessment: String = "",
    val verdict: String,
)
