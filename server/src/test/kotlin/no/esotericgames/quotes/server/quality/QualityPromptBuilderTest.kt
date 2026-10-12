package no.esotericgames.quotes.server.quality

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityPromptBuilderTest {

    @Test
    fun `the user content is the displayed text plus known metadata`() {
        val content = QualityPromptBuilder.buildUserContent("Only dull people are brilliant at breakfast.", "Oscar Wilde", null)

        assertEquals("QUOTATION\n\nOnly dull people are brilliant at breakfast.\n\nMETADATA\n\nAuthor: Oscar Wilde", content)
    }

    @Test
    fun `without metadata only the quotation is sent`() {
        assertEquals("QUOTATION\n\nText.", QualityPromptBuilder.buildUserContent("Text.", null, null))
    }

    @Test
    fun `the prompt resource loads and lists every problem the schema allows`() {
        val prompt = QualityPrompts.systemPrompt()

        QualityProblem.entries.forEach { assertTrue(it.name in prompt, "prompt does not define ${it.name}") }
        QualityVerdict.entries.forEach { assertTrue(it.dbValue in prompt, "prompt does not define ${it.dbValue}") }
    }

    @Test
    fun `config falls back to defaults and reads overrides`() {
        assertEquals(0.1, loadQualityConfig(MapApplicationConfig()).temperature)
        val config = loadQualityConfig(MapApplicationConfig("quality.llm.model" to "judge", "quality.llm.maxOutputTokens" to "1200"))
        assertEquals("judge", config.model)
        assertEquals(1200, config.maxOutputTokens)
    }
}
