package no.esotericgames.quotes.server.extraction

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExtractionConfigTest {

    @Test
    fun `falls back to in-code defaults when no extraction config is present`() {
        val config = loadExtractionConfig(MapApplicationConfig())

        assertEquals("http://localhost:8888", config.llm.baseUrl)
        assertEquals(55, config.policy.minSourceWords)
        assertNull(config.llm.reasoningEffort)
    }

    @Test
    fun `reads configured values when present`() {
        val rootConfig = MapApplicationConfig(
            "extraction.llm.baseUrl" to "http://example.local:1234",
            "extraction.llm.model" to "my-model",
            "extraction.llm.temperature" to "0.2",
            "extraction.llm.maxOutputTokens" to "500",
            "extraction.llm.requestTimeoutMillis" to "15000",
            "extraction.llm.reasoningEffort" to "low",
            "extraction.policy.minSourceWords" to "40",
        )

        val config = loadExtractionConfig(rootConfig)

        assertEquals("http://example.local:1234", config.llm.baseUrl)
        assertEquals("my-model", config.llm.model)
        assertEquals(0.2, config.llm.temperature)
        assertEquals(500, config.llm.maxOutputTokens)
        assertEquals(15000L, config.llm.requestTimeoutMillis)
        assertEquals("low", config.llm.reasoningEffort)
        assertEquals(40, config.policy.minSourceWords)
    }
}
