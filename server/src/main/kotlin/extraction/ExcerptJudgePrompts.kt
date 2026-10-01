package no.esotericgames.quotes.server.extraction

private const val STANDALONE_RESOURCE_PATH = "/prompts/excerpt-judge-standalone-v3.md"
private const val FIDELITY_RESOURCE_PATH = "/prompts/excerpt-judge-fidelity-v2.md"

/**
 * Loads the judge pass's two versioned system prompts, same resource convention as
 * [ExtractionPrompts]. Both prompts share one version label, since they are designed and tuned as a
 * pair; bump it (and add new resource files, keeping the old ones) whenever either prompt changes.
 *
 * v2 -> v3: only the standalone prompt changed, so the fidelity prompt is still its v2 file. The
 * standalone prompt now states a core standard for a strong quote, the dimensions to weigh (meaning,
 * standalone value, conceptual density, distinctiveness, reflective value, relevance, interpretive
 * depth), what not to require (truth, agreeableness, inspiration), and what to be skeptical of
 * (clichés, motivational language, vague lines, context-dependent dialogue). It adds a "would I keep
 * this without knowing the author?" test, a tie-break toward the lower level, and calibration examples
 * for a motivational cliché and a cynical but sharp line. Steps, levels, and hard rules are unchanged.
 */
object ExcerptJudgePrompts {
    const val PROMPT_VERSION = "excerpt-judge-v3"

    private val cachedStandalonePrompt: String by lazy { loadResource(STANDALONE_RESOURCE_PATH) }
    private val cachedFidelityPrompt: String by lazy { loadResource(FIDELITY_RESOURCE_PATH) }

    fun standaloneSystemPrompt(): String = cachedStandalonePrompt

    fun fidelitySystemPrompt(): String = cachedFidelityPrompt

    /** Deliberately contains only the excerpt text — see [ExcerptJudge] on why the blind call never sees the source. */
    fun buildStandaloneUserContent(excerptText: String, flaggedPhrases: List<String>): String {
        val quotation = "QUOTATION\n\n$excerptText"
        if (flaggedPhrases.isEmpty()) return quotation
        val phrases = flaggedPhrases.joinToString(", ") { "\"$it\"" }
        return "$quotation\n\nPay particular attention to whether a first-time reader can tell what these refer to: $phrases"
    }

    fun buildFidelityUserContent(sourceText: String, excerptText: String, standaloneReading: String): String =
        "SOURCE PASSAGE\n\n$sourceText\n\nEXCERPT\n\n$excerptText\n\nSTANDALONE READER'S UNDERSTANDING\n\n$standaloneReading"

    private fun loadResource(path: String): String {
        val stream = javaClass.getResourceAsStream(path) ?: error("bundled resource not found: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
