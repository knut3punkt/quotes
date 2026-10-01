package no.esotericgames.quotes.server.extraction.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val MAX_PHRASE_LENGTH = 80
private const val MAX_CORE_IDEA_LENGTH = 160
private const val MAX_REASON_LENGTH = 200

/**
 * Schema-constrained `response_format` for the broad, recall-oriented selection pass (prompt v4+,
 * see docs/features/quote-extraction.md's "Selector and judge" section). Kept as a Kotlin object
 * (not a resource file) so it can never drift from [ExcerptCandidateDto] — unlike the prose system
 * prompt, this is a machine contract that must stay in lock-step with the parser.
 *
 * Property order is deliberate: llama.cpp generates object properties in schema order, so the model
 * names the references it relies on and states the core idea right after choosing a range, rather
 * than finishing with a post-hoc justification. The selector no longer self-scores — quality scoring
 * moved to the separate judge pass, whose verdict decides acceptance.
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
                    listOf("startUnit", "endUnit", "references", "coreIdea", "reason").forEach { add(it) }
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
                    putJsonObject("references") {
                        put("type", "array")
                        put(
                            "description",
                            "Every pronoun, demonstrative, opening connective, or 'the X' in the excerpt that points " +
                                "to something mentioned elsewhere, with the unit id where that referent is stated. " +
                                "Empty if the excerpt has no such references.",
                        )
                        putJsonObject("items") {
                            put("type", "object")
                            put("additionalProperties", false)
                            putJsonArray("required") { add("phrase"); add("referentUnit") }
                            putJsonObject("properties") {
                                putJsonObject("phrase") {
                                    put("type", "string"); put("maxLength", MAX_PHRASE_LENGTH)
                                    put("description", "The referring word or phrase exactly as it appears in the excerpt.")
                                }
                                putJsonObject("referentUnit") {
                                    put("type", "integer")
                                    put("description", "The unit id where the thing it refers to is stated.")
                                }
                            }
                        }
                    }
                    putJsonObject("coreIdea") {
                        put("type", "string"); put("maxLength", MAX_CORE_IDEA_LENGTH)
                        put("description", "The general idea the excerpt expresses, in at most 15 words of your own.")
                    }
                    putJsonObject("reason") {
                        put("type", "string"); put("maxLength", MAX_REASON_LENGTH)
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
data class ReferenceDto(val phrase: String, val referentUnit: Int)

@Serializable
data class ExcerptCandidateDto(
    val startUnit: Int,
    val endUnit: Int,
    val references: List<ReferenceDto> = emptyList(),
    val coreIdea: String = "",
    val reason: String = "",
) {
    fun toRawCandidate() = RawExcerptCandidate(
        startUnit = startUnit,
        endUnit = endUnit,
        references = references.map { UnitReference(phrase = it.phrase, referentUnit = it.referentUnit) },
        coreIdea = coreIdea,
        reason = reason,
    )
}
