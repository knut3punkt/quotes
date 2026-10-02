package no.esotericgames.quotes.server.tagging

import io.ktor.server.config.ApplicationConfig

data class TaggingLlmConfig(
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxOutputTokens: Int,
    val requestTimeoutMillis: Long,
    val reasoningEffort: String? = null,
)

/**
 * How many tags of each facet one subject may receive, and how many existing tags per facet are shown
 * to the model as the vocabulary to prefer. The response schema carries looser hard ceilings
 * ([no.esotericgames.quotes.server.tagging.llm.MAX_CONCEPTS] etc.); these are the policy caps that
 * [TaggingValidator] applies.
 */
data class TaggingPolicy(
    val maxConcepts: Int,
    val maxMoods: Int,
    val maxMotifs: Int,
    val conceptHintSize: Int,
    val moodHintSize: Int,
    val motifHintSize: Int,
)

data class TaggingConfig(val llm: TaggingLlmConfig, val policy: TaggingPolicy)

val DEFAULT_TAGGING_LLM_CONFIG = TaggingLlmConfig(
    baseUrl = "http://localhost:8888",
    model = "local-model",
    temperature = 0.2,
    maxOutputTokens = 900,
    requestTimeoutMillis = 30_000,
    reasoningEffort = null,
)

val DEFAULT_TAGGING_POLICY = TaggingPolicy(
    maxConcepts = 9,
    maxMoods = 2,
    maxMotifs = 3,
    conceptHintSize = 150,
    moodHintSize = 60,
    motifHintSize = 80,
)

/**
 * Same typed-config pattern as [no.esotericgames.quotes.server.interpretation.loadInterpretationConfig],
 * with its own `tagging.llm` and `tagging.policy` HOCON blocks. Reads are all `propertyOrNull`-based
 * with in-code fallback defaults, so a test that never loads `application.conf` still works.
 */
fun loadTaggingConfig(rootConfig: ApplicationConfig): TaggingConfig {
    val llm = rootConfig.config("tagging.llm")
    val policy = rootConfig.config("tagging.policy")
    fun ApplicationConfig.int(key: String, default: Int) = propertyOrNull(key)?.getString()?.toInt() ?: default

    return TaggingConfig(
        llm = TaggingLlmConfig(
            baseUrl = llm.propertyOrNull("baseUrl")?.getString() ?: DEFAULT_TAGGING_LLM_CONFIG.baseUrl,
            model = llm.propertyOrNull("model")?.getString() ?: DEFAULT_TAGGING_LLM_CONFIG.model,
            temperature = llm.propertyOrNull("temperature")?.getString()?.toDouble()
                ?: DEFAULT_TAGGING_LLM_CONFIG.temperature,
            maxOutputTokens = llm.int("maxOutputTokens", DEFAULT_TAGGING_LLM_CONFIG.maxOutputTokens),
            requestTimeoutMillis = llm.propertyOrNull("requestTimeoutMillis")?.getString()?.toLong()
                ?: DEFAULT_TAGGING_LLM_CONFIG.requestTimeoutMillis,
            reasoningEffort = llm.propertyOrNull("reasoningEffort")?.getString(),
        ),
        policy = TaggingPolicy(
            maxConcepts = policy.int("maxConcepts", DEFAULT_TAGGING_POLICY.maxConcepts),
            maxMoods = policy.int("maxMoods", DEFAULT_TAGGING_POLICY.maxMoods),
            maxMotifs = policy.int("maxMotifs", DEFAULT_TAGGING_POLICY.maxMotifs),
            conceptHintSize = policy.int("conceptHintSize", DEFAULT_TAGGING_POLICY.conceptHintSize),
            moodHintSize = policy.int("moodHintSize", DEFAULT_TAGGING_POLICY.moodHintSize),
            motifHintSize = policy.int("motifHintSize", DEFAULT_TAGGING_POLICY.motifHintSize),
        ),
    )
}
