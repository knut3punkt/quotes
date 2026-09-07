package no.esotericgames.quotes.server.importing

import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizationTest {

    @Test
    fun `normalizeQuoteText lowercases and collapses whitespace`() {
        assertEquals(
            "the only true wisdom is in knowing you know nothing",
            normalizeQuoteText("  The Only\n  True   Wisdom  is in knowing you know nothing.  "),
        )
    }

    @Test
    fun `normalizeQuoteText converts curly quotes and dashes to plain equivalents`() {
        assertEquals(
            "man's search for meaning - a survivor's tale",
            normalizeQuoteText("Man’s Search for Meaning — A Survivor’s Tale."),
        )
    }

    @Test
    fun `normalizeQuoteText strips trailing punctuation but keeps internal punctuation`() {
        assertEquals(
            "hypotheses are scaffoldings; you mustn't mistake the scaffolding for the building",
            normalizeQuoteText("Hypotheses are scaffoldings; you mustn't mistake the scaffolding for the building!"),
        )
    }

    @Test
    fun `normalizeQuoteText treats differently-punctuated renderings of the same quote as equal`() {
        val a = normalizeQuoteText("Everything should be made as simple as possible, but no simpler.")
        val b = normalizeQuoteText("everything should be made as simple as possible, but no simpler")
        assertEquals(a, b)
    }

    @Test
    fun `normalizeAuthorName lowercases, trims, and collapses whitespace`() {
        assertEquals("carl jung", normalizeAuthorName("  CARL   Jung  "))
    }

    @Test
    fun `sha256Hex is deterministic and hex-encoded`() {
        val hash = sha256Hex("Carl Jung I must also have a dark side if I am to be whole.")
        assertEquals(64, hash.length)
        assertEquals(hash, sha256Hex("Carl Jung I must also have a dark side if I am to be whole."))
    }
}
