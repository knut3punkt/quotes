package no.esotericgames.quotes.server.quality

import io.ktor.server.config.ApplicationConfig

data class QualityLlmConfig(
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxOutputTokens: Int,
    val requestTimeoutMillis: Long,
    val reasoningEffort: String? = null,
)

private val DEFAULT_LLM_CONFIG = QualityLlmConfig(
    baseUrl = "http://localhost:8888",
    model = "local-model",
    temperature = 0.1,
    maxOutputTokens = 900,
    requestTimeoutMillis = 60_000,
    reasoningEffort = null,
)

/**
 * Same typed-config pattern as [no.esotericgames.quotes.server.interpretation.loadInterpretationConfig],
 * with its own `quality.llm` HOCON block. A low temperature, since this is a judgment rather than
 * generation, and runs are meant to be comparable.
 */
fun loadQualityConfig(rootConfig: ApplicationConfig): QualityLlmConfig {
    val llm = rootConfig.config("quality.llm")
    return QualityLlmConfig(
        baseUrl = llm.propertyOrNull("baseUrl")?.getString() ?: DEFAULT_LLM_CONFIG.baseUrl,
        model = llm.propertyOrNull("model")?.getString() ?: DEFAULT_LLM_CONFIG.model,
        temperature = llm.propertyOrNull("temperature")?.getString()?.toDouble() ?: DEFAULT_LLM_CONFIG.temperature,
        maxOutputTokens = llm.propertyOrNull("maxOutputTokens")?.getString()?.toInt() ?: DEFAULT_LLM_CONFIG.maxOutputTokens,
        requestTimeoutMillis = llm.propertyOrNull("requestTimeoutMillis")?.getString()?.toLong()
            ?: DEFAULT_LLM_CONFIG.requestTimeoutMillis,
        reasoningEffort = llm.propertyOrNull("reasoningEffort")?.getString(),
    )
}
