package no.esotericgames.quotes.server.interpretation

private const val PROMPT_RESOURCE_PATH = "/prompts/quote-interpretation-v1.md"

/**
 * Loads the versioned runtime system prompt as a classpath resource (never an inline Kotlin string),
 * mirroring [no.esotericgames.quotes.server.extraction.ExtractionPrompts]'s
 * `javaClass.getResourceAsStream(...)` convention. The prompt text is docs/features/quote-interpretations.md's
 * "Runtime system prompt — version 1" section, copied verbatim.
 */
object InterpretationPrompts {
    const val PROMPT_VERSION = "quote-interpretation-v1"

    private val cachedPrompt: String by lazy { loadResource() }

    fun systemPrompt(): String = cachedPrompt

    private fun loadResource(): String {
        val stream = javaClass.getResourceAsStream(PROMPT_RESOURCE_PATH)
            ?: error("bundled resource not found: $PROMPT_RESOURCE_PATH")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
