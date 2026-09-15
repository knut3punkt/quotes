package no.esotericgames.quotes.server.wikiquote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fixtures below are transcribed from real `action=raw` wikitext (Nietzsche, Churchill, Marcus
 * Aurelius, Einstein) fetched while designing these heuristics, not hand-invented shapes.
 */
class SourceHintExtractorTest {

    @Test
    fun `heading supplies the title and year when the citation is just a page number`() {
        // Nietzsche, "The Birth of Tragedy (1872)": translation candidate consumed, "p. 98" left.
        val hints = extractSourceHints(
            sectionPath = listOf("The Birth of Tragedy (1872)"),
            citations = listOf("But what changes come upon the weary desert of our culture...", "p. 98"),
            consumedTranslationCitation = "But what changes come upon the weary desert of our culture...",
        )

        assertEquals("The Birth of Tragedy", hints.title)
        assertEquals(1872, hints.year)
        assertEquals("p. 98", hints.location)
    }

    @Test
    fun `a work title embedded in the citation is preferred over a generic period heading`() {
        // Churchill, "Early career (1897-1929)": the heading is a life period, not a work title;
        // the real work title only appears inside the citation itself.
        val hints = extractSourceHints(
            sectionPath = listOf("Early career (1897–1929)"),
            citations = listOf("The Story of the Malakand Field Force (1898), Chapter III"),
            consumedTranslationCitation = null,
        )

        assertEquals("The Story of the Malakand Field Force", hints.title)
        assertEquals(1898, hints.year)
        assertEquals("Chapter III", hints.location)
    }

    @Test
    fun `a book-verse numeral citation is picked over a bare original-language citation`() {
        // Marcus Aurelius, "Meditations": Greek original text is a nested citation alongside the
        // "II, 15" book-verse locator; the Greek must not be picked as the location.
        val hints = extractSourceHints(
            sectionPath = listOf("Meditations"),
            citations = listOf("὏τι πᾶν ὑπόληψις.", "II, 15"),
            consumedTranslationCitation = null,
        )

        assertEquals("Meditations", hints.title)
        assertNull(hints.year)
        assertEquals("II, 15", hints.location)
    }

    @Test
    fun `a decade heading yields no title and multiple ambiguous citations yield no location`() {
        // Einstein, "1900s": a pure decade label is never a work title, and with no page/verse
        // marker among several leftover citations it's better to leave location blank than guess.
        val hints = extractSourceHints(
            sectionPath = listOf("1900s"),
            citations = listOf(
                "Another translation: Authority gone to one's head is the greatest enemy of truth.",
                "Letter to Jost Winteler (July 8th, 1901), quoted in The Private Lives of Albert Einstein",
            ),
            consumedTranslationCitation = null,
        )

        assertNull(hints.title)
        assertNull(hints.year)
        assertNull(hints.location)
    }

    @Test
    fun `a translator-attributed verse citation is not mistaken for a work title`() {
        // Rumi, "Masnavi": "(tr. Helminski, 1990)" must not be parsed as a "(YYYY)" work-year
        // suffix — the parenthesized text isn't purely a 4-digit year.
        val hints = extractSourceHints(
            sectionPath = listOf("Masnavi"),
            citations = listOf("I, 232-3 (tr. Helminski, 1990)"),
            consumedTranslationCitation = null,
        )

        assertEquals("Masnavi", hints.title)
        assertNull(hints.year)
        assertEquals("I, 232-3 (tr. Helminski, 1990)", hints.location)
    }

    @Test
    fun `no sectionPath and no structured citation yields all-null hints`() {
        val hints = extractSourceHints(
            sectionPath = emptyList(),
            citations = listOf("I have the simplest tastes."),
            consumedTranslationCitation = null,
        )

        assertNull(hints.title)
        assertNull(hints.year)
        assertEquals("I have the simplest tastes.", hints.location)
    }
}
