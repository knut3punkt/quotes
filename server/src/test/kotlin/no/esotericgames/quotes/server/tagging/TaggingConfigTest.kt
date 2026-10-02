package no.esotericgames.quotes.server.tagging

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaggingConfigTest {

    @Test
    fun `falls back to in-code defaults when no tagging config is present`() {
        val config = loadTaggingConfig(MapApplicationConfig())

        assertEquals(DEFAULT_TAGGING_LLM_CONFIG, config.llm)
        assertEquals(DEFAULT_TAGGING_POLICY, config.policy)
        assertNull(config.llm.reasoningEffort)
    }

    @Test
    fun `reads configured values when present`() {
        val rootConfig = MapApplicationConfig(
            "tagging.llm.baseUrl" to "http://example.local:1234",
            "tagging.llm.model" to "my-model",
            "tagging.llm.temperature" to "0.3",
            "tagging.llm.maxOutputTokens" to "700",
            "tagging.llm.requestTimeoutMillis" to "15000",
            "tagging.llm.reasoningEffort" to "low",
            "tagging.policy.maxConcepts" to "6",
            "tagging.policy.maxMoods" to "1",
            "tagging.policy.maxMotifs" to "2",
            "tagging.policy.conceptHintSize" to "40",
            "tagging.policy.moodHintSize" to "20",
            "tagging.policy.motifHintSize" to "30",
        )

        val config = loadTaggingConfig(rootConfig)

        assertEquals(TaggingLlmConfig("http://example.local:1234", "my-model", 0.3, 700, 15000L, "low"), config.llm)
        assertEquals(TaggingPolicy(6, 1, 2, 40, 20, 30), config.policy)
    }
}
