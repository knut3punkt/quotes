package no.esotericgames.quotes.server.interpretation

import io.ktor.server.config.ApplicationConfig

data class InterpretationLlmConfig(
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxOutputTokens: Int,
    val requestTimeoutMillis: Long,
    val reasoningEffort: String? = null,
)

private val DEFAULT_LLM_CONFIG = InterpretationLlmConfig(
    baseUrl = "http://localhost:8888",
    model = "local-model",
    temperature = 0.5,
    maxOutputTokens = 800,
    requestTimeoutMillis = 30_000,
    reasoningEffort = null,
)

/**
 * Same typed-config pattern as [no.esotericgames.quotes.server.extraction.loadExtractionConfig], with
 * its own `interpretation.llm` HOCON block and its own default temperature (~0.5 vs extraction's
 * ~0.1) per docs/features/quote-interpretations.md's "Suggested generation settings" — interpretation
 * benefits from more diversity than deterministic excerpt selection. There is no `policy` sub-block:
 * unlike extraction, interpretations have no length gate or score-threshold gate (see
 * [InterpretationValidator] and the doc's "do not automatically trust model-generated scores"
 * guidance).
 *
 * Reads are all `propertyOrNull`-based with in-code fallback defaults, mirroring extraction's config
 * loader, so this never breaks a test that constructs the application without loading
 * `application.conf` at all.
 */
fun loadInterpretationConfig(rootConfig: ApplicationConfig): InterpretationLlmConfig {
    val llm = rootConfig.config("interpretation.llm")
    return InterpretationLlmConfig(
        baseUrl = llm.propertyOrNull("baseUrl")?.getString() ?: DEFAULT_LLM_CONFIG.baseUrl,
        model = llm.propertyOrNull("model")?.getString() ?: DEFAULT_LLM_CONFIG.model,
        temperature = llm.propertyOrNull("temperature")?.getString()?.toDouble() ?: DEFAULT_LLM_CONFIG.temperature,
        maxOutputTokens = llm.propertyOrNull("maxOutputTokens")?.getString()?.toInt() ?: DEFAULT_LLM_CONFIG.maxOutputTokens,
        requestTimeoutMillis = llm.propertyOrNull("requestTimeoutMillis")?.getString()?.toLong()
            ?: DEFAULT_LLM_CONFIG.requestTimeoutMillis,
        reasoningEffort = llm.propertyOrNull("reasoningEffort")?.getString(),
    )
}
