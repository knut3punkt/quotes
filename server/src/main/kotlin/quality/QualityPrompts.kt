package no.esotericgames.quotes.server.quality

private const val PROMPT_RESOURCE_PATH = "/prompts/quote-quality-v1.md"

/**
 * Loads the versioned quality-assessment system prompt as a classpath resource, same convention as
 * [no.esotericgames.quotes.server.interpretation.InterpretationPrompts]. External runners (a frontier
 * model following the same instructions) fetch this exact text through the admin API.
 */
object QualityPrompts {
    const val PROMPT_VERSION = "quote-quality-v1"

    private val cachedPrompt: String by lazy { loadResource() }

    fun systemPrompt(): String = cachedPrompt

    private fun loadResource(): String {
        val stream = javaClass.getResourceAsStream(PROMPT_RESOURCE_PATH)
            ?: error("bundled resource not found: $PROMPT_RESOURCE_PATH")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
