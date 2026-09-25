package no.esotericgames.quotes.server.interpretation.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Shared with InterpretationValidator, which re-checks these limits application-side rather than
// trusting the schema-constrained response alone.
const val MAX_INTERPRETATIONS = 4
const val MAX_LENS_LENGTH = 60
const val MAX_INTERPRETATION_LENGTH = 600

/**
 * Schema-constrained `response_format` for llama-server's OpenAI-compatible chat-completions
 * endpoint, matching docs/features/quote-interpretations.md's "Structured response schema" section.
 * Kept as a Kotlin object (not a resource file), same rationale as extraction's
 * `EXCERPT_SELECTION_JSON_SCHEMA`, so it can never drift from [InterpretationCandidateDto].
 *
 * Exact-duplicate detection, reasonable-length rejection beyond these soft schema caps, and score
 * clamping cannot be expressed here and are validated deterministically in
 * [no.esotericgames.quotes.server.interpretation.InterpretationValidator] instead.
 */
val INTERPRETATION_JSON_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonArray("required") { add("interpretations") }
    putJsonObject("properties") {
        putJsonObject("interpretations") {
            put("type", "array")
            put("maxItems", MAX_INTERPRETATIONS)
            putJsonObject("items") {
                put("type", "object")
                put("additionalProperties", false)
                putJsonArray("required") { listOf("lens", "interpretation", "textualSupport", "speculativeness").forEach { add(it) } }
                putJsonObject("properties") {
                    putJsonObject("lens") {
                        put("type", "string")
                        put("maxLength", MAX_LENS_LENGTH)
                        put("description", "A short label for the interpretive lens, chosen to fit this specific reading (e.g. \"Psychological\", \"Existential\", \"Symbolic\"). Do not force a fixed set of categories.")
                    }
                    putJsonObject("interpretation") {
                        put("type", "string")
                        put("maxLength", MAX_INTERPRETATION_LENGTH)
                        put("description", "1-3 sentences explaining this specific reading of the quotation. Interpretation, not paraphrase.")
                    }
                    putJsonObject("textualSupport") {
                        put("type", "integer"); put("minimum", 0); put("maximum", 100)
                        put(
                            "description",
                            "Integer from 0 to 100: how strongly this reading can be grounded in the words and " +
                                "structure of the supplied quotation. Not whether the reading is true. Use the full " +
                                "range, not just 0 or 1.",
                        )
                    }
                    putJsonObject("speculativeness") {
                        put("type", "integer"); put("minimum", 0); put("maximum", 100)
                        put(
                            "description",
                            "Integer from 0 to 100: how much additional conceptual framing or assumption this " +
                                "reading requires beyond the explicit surface content. A speculative interpretation " +
                                "is not automatically inferior. Use the full range, not just 0 or 1.",
                        )
                    }
                }
            }
        }
    }
}

val INTERPRETATION_RESPONSE_FORMAT: JsonObject = buildJsonObject {
    put("type", "json_schema")
    putJsonObject("json_schema") {
        put("name", "quote_interpretations")
        put("strict", true)
        put("schema", INTERPRETATION_JSON_SCHEMA)
    }
}

@Serializable
data class InterpretationResponseDto(val interpretations: List<InterpretationCandidateDto> = emptyList())

@Serializable
data class InterpretationCandidateDto(
    val lens: String,
    val interpretation: String,
    val textualSupport: Int,
    val speculativeness: Int,
) {
    fun toRawCandidate() = RawInterpretationCandidate(
        lens = lens,
        interpretation = interpretation,
        textualSupport = textualSupport,
        speculativeness = speculativeness,
    )
}
