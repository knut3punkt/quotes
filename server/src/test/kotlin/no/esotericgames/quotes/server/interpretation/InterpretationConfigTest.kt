package no.esotericgames.quotes.server.interpretation

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterpretationConfigTest {

    @Test
    fun `falls back to in-code defaults when no interpretation config is present`() {
        val config = loadInterpretationConfig(MapApplicationConfig())

        assertEquals("http://localhost:8888", config.baseUrl)
        assertEquals("local-model", config.model)
        assertEquals(0.5, config.temperature)
        assertEquals(1400, config.maxOutputTokens)
        assertNull(config.reasoningEffort)
    }

    @Test
    fun `reads configured values when present`() {
        val rootConfig = MapApplicationConfig(
            "interpretation.llm.baseUrl" to "http://example.local:1234",
            "interpretation.llm.model" to "my-model",
            "interpretation.llm.temperature" to "0.6",
            "interpretation.llm.maxOutputTokens" to "500",
            "interpretation.llm.requestTimeoutMillis" to "15000",
            "interpretation.llm.reasoningEffort" to "low",
        )

        val config = loadInterpretationConfig(rootConfig)

        assertEquals("http://example.local:1234", config.baseUrl)
        assertEquals("my-model", config.model)
        assertEquals(0.6, config.temperature)
        assertEquals(500, config.maxOutputTokens)
        assertEquals(15000L, config.requestTimeoutMillis)
        assertEquals("low", config.reasoningEffort)
    }
}
