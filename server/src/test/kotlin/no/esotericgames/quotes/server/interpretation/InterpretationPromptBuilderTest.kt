package no.esotericgames.quotes.server.interpretation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterpretationPromptBuilderTest {

    @Test
    fun `quote-only content has no metadata block`() {
        val content = InterpretationPromptBuilder.buildUserContent(QuoteInterpretationContext(text = "The unexamined life is not worth living."))

        assertEquals("QUOTE:\n\nThe unexamined life is not worth living.", content)
        assertFalse(content.contains("METADATA"))
    }

    @Test
    fun `author, source, and source type are appended as a distinct metadata block`() {
        val content = InterpretationPromptBuilder.buildUserContent(
            QuoteInterpretationContext(
                text = "The unexamined life is not worth living.",
                authorName = "Socrates",
                sourceTitle = "Apology",
                sourceTypeCode = "philosophical-work",
            ),
        )

        assertEquals(
            "QUOTE:\n\nThe unexamined life is not worth living.\n\nMETADATA:\n\nAuthor: Socrates\nSource: Apology\nSource type: philosophical-work",
            content,
        )
    }

    @Test
    fun `only the resolvable metadata fields are included`() {
        val content = InterpretationPromptBuilder.buildUserContent(
            QuoteInterpretationContext(text = "A quote.", authorName = "Someone"),
        )

        assertTrue(content.contains("Author: Someone"))
        assertFalse(content.contains("Source:"))
        assertFalse(content.contains("Source type:"))
    }

    @Test
    fun `an excerpt subject with original context produces the QUOTE TO INTERPRET-ORIGINAL CONTEXT format`() {
        val content = InterpretationPromptBuilder.buildUserContent(
            QuoteInterpretationContext(
                text = "Know thyself.",
                originalContext = "The oracle at Delphi bore the inscription: know thyself. Many visitors puzzled over its meaning.",
            ),
        )

        assertEquals(
            "QUOTE TO INTERPRET:\n\nKnow thyself.\n\nORIGINAL CONTEXT:\n\nThe oracle at Delphi bore the inscription: know thyself. Many visitors puzzled over its meaning.",
            content,
        )
        assertFalse(content.contains("QUOTE:\n"))
    }

    @Test
    fun `metadata still appends after the original context block when both are present`() {
        val content = InterpretationPromptBuilder.buildUserContent(
            QuoteInterpretationContext(
                text = "Know thyself.",
                originalContext = "The oracle at Delphi bore the inscription: know thyself.",
                authorName = "Delphic maxim",
            ),
        )

        assertEquals(
            "QUOTE TO INTERPRET:\n\nKnow thyself.\n\nORIGINAL CONTEXT:\n\nThe oracle at Delphi bore the inscription: know thyself.\n\nMETADATA:\n\nAuthor: Delphic maxim",
            content,
        )
    }

    @Test
    fun `system prompt resource loads and is non-blank`() {
        val prompt = InterpretationPrompts.systemPrompt()

        assertTrue(prompt.isNotBlank())
        assertTrue(prompt.contains("interpretation"))
    }

    @Test
    fun `prompt version matches the resource filename stem`() {
        assertEquals("quote-interpretation-v1", InterpretationPrompts.PROMPT_VERSION)
    }
}
