package no.esotericgames.quotes.server.tagging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaggingPromptBuilderTest {

    @Test
    fun `a bare quote with an empty vocabulary has the quote and a none-yet vocabulary block only`() {
        val content = TaggingPromptBuilder.buildUserContent(QuoteTaggingContext(text = "Know thyself."), VocabularyHint.EMPTY)

        assertTrue(content.startsWith("QUOTE:\n\nKnow thyself."))
        assertFalse(content.contains("METADATA"))
        assertFalse(content.contains("INTERPRETATIONS"))
        assertFalse(content.contains("ORIGINAL CONTEXT"))
        assertTrue(content.contains("Concepts: (none yet)\nMoods: (none yet)\nMotifs: (none yet)"))
    }

    @Test
    fun `an excerpt is labelled as the quote to tag, with its parent as original context`() {
        val content = TaggingPromptBuilder.buildUserContent(
            QuoteTaggingContext(text = "Short part.", originalContext = "Longer passage. Short part."),
            VocabularyHint.EMPTY,
        )

        assertTrue(content.startsWith("QUOTE TO TAG:\n\nShort part.\n\nORIGINAL CONTEXT:\n\nLonger passage. Short part."))
    }

    @Test
    fun `metadata, numbered interpretations and the existing vocabulary are included`() {
        val content = TaggingPromptBuilder.buildUserContent(
            QuoteTaggingContext(
                text = "Except a man be born again, he cannot see the kingdom of God.",
                authorName = "John",
                sourceTitle = "Bible",
                interpretations = listOf(
                    InterpretationContext("Christian", "Spiritual rebirth into faith.", 94, 10),
                    InterpretationContext("Psychological", "Perception must change.", 67, 58),
                ),
            ),
            VocabularyHint(concepts = listOf("faith", "death"), moods = listOf("solemn"), motifs = emptyList()),
        )

        assertTrue(content.contains("METADATA:\n\nAuthor: John\nSource: Bible"))
        assertTrue(
            content.contains(
                "INTERPRETATIONS:\n\n" +
                    "1. [Christian; textual support 94/100, speculativeness 10/100] Spiritual rebirth into faith.\n" +
                    "2. [Psychological; textual support 67/100, speculativeness 58/100] Perception must change.",
            ),
        )
        assertTrue(content.endsWith("Concepts: faith, death\nMoods: solemn\nMotifs: (none yet)"))
    }

    @Test
    fun `prompt resource loads and its version matches the resource filename stem`() {
        assertTrue(TaggingPrompts.systemPrompt().contains("MOTIFS"))
        assertEquals("quote-tagging-v1", TaggingPrompts.PROMPT_VERSION)
    }
}
