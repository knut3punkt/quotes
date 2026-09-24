package no.esotericgames.quotes.server.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractionPromptBuilderTest {

    @Test
    fun `formats numbered source units exactly`() {
        val units = listOf(
            SourceUnit(id = 1, startOffset = 0, endOffset = 5, text = "Alpha"),
            SourceUnit(id = 2, startOffset = 6, endOffset = 12, text = "Beta."),
        )

        val content = ExtractionPromptBuilder.buildUserContent(units)

        assertEquals(
            "SOURCE UNITS\n\n[1] Alpha\n[2] Beta.\n\nSelect zero or more quotable standalone excerpts according to the extraction rules.",
            content,
        )
    }

    @Test
    fun `system prompt resource loads and is non-blank`() {
        val prompt = ExtractionPrompts.systemPrompt()

        assertTrue(prompt.isNotBlank())
        assertTrue(prompt.contains("extraction"))
    }

    @Test
    fun `prompt version matches the resource filename stem`() {
        assertEquals("quote-extraction-v3", ExtractionPrompts.PROMPT_VERSION)
    }
}
