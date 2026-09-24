package no.esotericgames.quotes.server.extraction.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Schema-constrained `response_format` for llama-server's OpenAI-compatible chat-completions
 * endpoint, matching docs/features/quote-extraction.md's "Structured model response" section. Kept
 * as a Kotlin object (not a resource file) so it can never drift from [ExcerptCandidateDto] — unlike
 * the prose system prompt, this is a machine contract that must stay in lock-step with the parser.
 *
 * `endUnit >= startUnit`, unit-id validity, word count, and duplicate/overlap resolution cannot be
 * expressed here and are validated deterministically in [no.esotericgames.quotes.server.extraction.ExtractionValidator]
 * instead. There is deliberately no maximum-item-count constraint on `excerpts` — see
 * [no.esotericgames.quotes.server.extraction.ExtractionPolicy].
 */
val EXCERPT_SELECTION_JSON_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonArray("required") { add("excerpts") }
    putJsonObject("properties") {
        putJsonObject("excerpts") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "object")
                put("additionalProperties", false)
                putJsonArray("required") {
                    listOf("startUnit", "endUnit", "independence", "completeness", "quotability", "contextualFidelity", "reason")
                        .forEach { add(it) }
                }
                putJsonObject("properties") {
                    putJsonObject("startUnit") {
                        put("type", "integer")
                        put("description", "The id of the first selected unit (1-based, from the numbered SOURCE UNITS).")
                    }
                    putJsonObject("endUnit") {
                        put("type", "integer")
                        put("description", "The id of the last selected unit. Equal to startUnit for a single unit.")
                    }
                    putJsonObject("independence") {
                        put("type", "integer"); put("minimum", 0); put("maximum", 100)
                        put(
                            "description",
                            "Integer from 0 to 100: how understandable the excerpt is without the omitted " +
                                "surrounding text. 0 means it is incomprehensible alone; 100 means it needs no " +
                                "outside context at all. Use the full range, not just 0 or 1.",
                        )
                    }
                    putJsonObject("completeness") {
                        put("type", "integer"); put("minimum", 0); put("maximum", 100)
                        put(
                            "description",
                            "Integer from 0 to 100: how fully the excerpt expresses a coherent, finished " +
                                "thought. 0 means it is an incomplete fragment; 100 means it is fully self-contained. " +
                                "Use the full range, not just 0 or 1.",
                        )
                    }
                    putJsonObject("quotability") {
                        put("type", "integer"); put("minimum", 0); put("maximum", 100)
                        put(
                            "description",
                            "Integer from 0 to 100: how well the excerpt functions as a meaningful standalone " +
                                "quotation — a generalizable observation, claim, insight, or principle a reader " +
                                "outside the source would find worth repeating. This is NOT about intensity, drama, " +
                                "or emotion: a vivid personal complaint or accusation that only makes sense within " +
                                "its own dispute should score low here even if it is complete and independent. " +
                                "0 means it is not worth quoting; 100 means it is highly quotable. Use the full " +
                                "range, not just 0 or 1.",
                        )
                    }
                    putJsonObject("contextualFidelity") {
                        put("type", "integer"); put("minimum", 0); put("maximum", 100)
                        put(
                            "description",
                            "Integer from 0 to 100: how safely the excerpt preserves the meaning of the original " +
                                "passage once the surrounding text is removed. 0 means it now asserts something " +
                                "materially different; 100 means the meaning is fully preserved. Use the full range, " +
                                "not just 0 or 1.",
                        )
                    }
                    putJsonObject("reason") {
                        put("type", "string"); put("maxLength", 200)
                        put("description", "A short diagnostic explanation, not shown to end users.")
                    }
                }
            }
        }
    }
}

val EXCERPT_SELECTION_RESPONSE_FORMAT: JsonObject = buildJsonObject {
    put("type", "json_schema")
    putJsonObject("json_schema") {
        put("name", "excerpt_selection")
        put("strict", true)
        put("schema", EXCERPT_SELECTION_JSON_SCHEMA)
    }
}

@Serializable
data class ExcerptSelectionResponseDto(val excerpts: List<ExcerptCandidateDto> = emptyList())

@Serializable
data class ExcerptCandidateDto(
    val startUnit: Int,
    val endUnit: Int,
    val independence: Int,
    val completeness: Int,
    val quotability: Int,
    val contextualFidelity: Int,
    val reason: String = "",
) {
    fun toRawCandidate() = RawExcerptCandidate(
        startUnit = startUnit,
        endUnit = endUnit,
        independence = independence,
        completeness = completeness,
        quotability = quotability,
        contextualFidelity = contextualFidelity,
        reason = reason,
    )
}
