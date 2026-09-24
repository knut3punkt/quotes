package no.esotericgames.quotes.server.extraction

import io.ktor.server.config.ApplicationConfig

data class ExtractionLlmConfig(
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxOutputTokens: Int,
    val requestTimeoutMillis: Long,
    val reasoningEffort: String? = null,
)

data class ExtractionConfig(
    val llm: ExtractionLlmConfig,
    val policy: ExtractionPolicy,
)

private val DEFAULT_LLM_CONFIG = ExtractionLlmConfig(
    baseUrl = "http://localhost:8888",
    model = "local-model",
    temperature = 0.1,
    maxOutputTokens = 800,
    requestTimeoutMillis = 30_000,
    reasoningEffort = null,
)
private val DEFAULT_POLICY = ExtractionPolicy()

/**
 * The codebase's first typed feature-config class: every other outbound client hardcodes its
 * settings as `private const val`s, which cannot satisfy "must be configurable" here (consts have no
 * `${?VAR}` override), and this feature has ~9 interrelated settings shared across two classes.
 *
 * Reads are all `propertyOrNull`-based with in-code fallback defaults (rather than throwing when a
 * key is absent) so this never breaks a test that constructs the application without loading
 * `application.conf` at all (see `ApplicationTest`'s first two tests) — the fallbacks mirror
 * `application.conf`'s own literal defaults, which take over as soon as that file is loaded.
 */
fun loadExtractionConfig(rootConfig: ApplicationConfig): ExtractionConfig {
    val llm = rootConfig.config("extraction.llm")
    val policy = rootConfig.config("extraction.policy")
    return ExtractionConfig(
        llm = ExtractionLlmConfig(
            baseUrl = llm.propertyOrNull("baseUrl")?.getString() ?: DEFAULT_LLM_CONFIG.baseUrl,
            model = llm.propertyOrNull("model")?.getString() ?: DEFAULT_LLM_CONFIG.model,
            temperature = llm.propertyOrNull("temperature")?.getString()?.toDouble() ?: DEFAULT_LLM_CONFIG.temperature,
            maxOutputTokens = llm.propertyOrNull("maxOutputTokens")?.getString()?.toInt() ?: DEFAULT_LLM_CONFIG.maxOutputTokens,
            requestTimeoutMillis = llm.propertyOrNull("requestTimeoutMillis")?.getString()?.toLong()
                ?: DEFAULT_LLM_CONFIG.requestTimeoutMillis,
            reasoningEffort = llm.propertyOrNull("reasoningEffort")?.getString(),
        ),
        policy = ExtractionPolicy(
            minSourceWords = policy.propertyOrNull("minSourceWords")?.getString()?.toInt() ?: DEFAULT_POLICY.minSourceWords,
            preferredMinWords = policy.propertyOrNull("preferredMinWords")?.getString()?.toInt() ?: DEFAULT_POLICY.preferredMinWords,
            preferredMaxWords = policy.propertyOrNull("preferredMaxWords")?.getString()?.toInt() ?: DEFAULT_POLICY.preferredMaxWords,
            acceptableMinWords = policy.propertyOrNull("acceptableMinWords")?.getString()?.toInt() ?: DEFAULT_POLICY.acceptableMinWords,
            acceptableMaxWords = policy.propertyOrNull("acceptableMaxWords")?.getString()?.toInt() ?: DEFAULT_POLICY.acceptableMaxWords,
            minIndependence = policy.propertyOrNull("minIndependence")?.getString()?.toInt() ?: DEFAULT_POLICY.minIndependence,
            minCompleteness = policy.propertyOrNull("minCompleteness")?.getString()?.toInt() ?: DEFAULT_POLICY.minCompleteness,
            minContextualFidelity = policy.propertyOrNull("minContextualFidelity")?.getString()?.toInt()
                ?: DEFAULT_POLICY.minContextualFidelity,
            minQuotability = policy.propertyOrNull("minQuotability")?.getString()?.toInt() ?: DEFAULT_POLICY.minQuotability,
        ),
    )
}
