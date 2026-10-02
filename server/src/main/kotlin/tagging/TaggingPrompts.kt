package no.esotericgames.quotes.server.tagging

private const val PROMPT_RESOURCE_PATH = "/prompts/quote-tagging-v1.md"

/**
 * Loads the versioned runtime system prompt as a classpath resource, same convention as
 * [no.esotericgames.quotes.server.interpretation.InterpretationPrompts]. When the prompt changes, add a
 * new resource file, keep the old one for provenance, and bump [PROMPT_VERSION].
 */
object TaggingPrompts {
    const val PROMPT_VERSION = "quote-tagging-v1"

    private val cachedPrompt: String by lazy { loadResource() }

    fun systemPrompt(): String = cachedPrompt

    private fun loadResource(): String {
        val stream = javaClass.getResourceAsStream(PROMPT_RESOURCE_PATH)
            ?: error("bundled resource not found: $PROMPT_RESOURCE_PATH")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
