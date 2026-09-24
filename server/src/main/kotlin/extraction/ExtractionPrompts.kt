package no.esotericgames.quotes.server.extraction

private const val PROMPT_RESOURCE_PATH = "/prompts/quote-extraction-v3.md"

/**
 * Loads the versioned runtime system prompt as a classpath resource (never an inline Kotlin
 * string), following the same `javaClass.getResourceAsStream(...)` convention used by the scripture
 * importers (e.g. `TaoTeChingImportService`) for their bundled data files.
 *
 * v1 -> v2: added explicit 0-100 scoring guidance to the prompt itself. v1 only carried that
 * guidance in the JSON schema's per-field `description`s, which was not enough for at least one
 * local model (a small quantized model) to avoid answering every score with a bare 0 or 1, as if
 * the fields were booleans.
 *
 * v2 -> v3: defined what each of the four scores actually measures, and specifically clarified that
 * quotability rewards a generalizable observation/insight/principle, not intensity or drama — the
 * model had been scoring emotional personal outbursts (e.g. an angry rebuke) highly on quotability
 * purely because they were vivid and complete, with no idea beyond their original dispute.
 *
 * Earlier prompt versions are kept for historical reference/provenance, not deleted.
 */
object ExtractionPrompts {
    const val PROMPT_VERSION = "quote-extraction-v3"

    private val cachedPrompt: String by lazy { loadResource() }

    fun systemPrompt(): String = cachedPrompt

    private fun loadResource(): String {
        val stream = javaClass.getResourceAsStream(PROMPT_RESOURCE_PATH)
            ?: error("bundled resource not found: $PROMPT_RESOURCE_PATH")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
