package no.esotericgames.quotes.server.sources.wikiquote

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WikiquoteImportServiceTest {

    @Test
    fun `resolveImportText falls back to the first citation when the heuristic finds no candidate`() {
        // Every nested citation looks citation-like (per looksLikeCitation), so the primary
        // heuristic finds nothing even though the quote text is non-English — per the product
        // scope of English-only quotes, the first citation is used as a last-resort guess rather
        // than leaving the foreign text unresolved.
        val quote = ParsedQuote(
            text = "Où est la vérité?",
            citations = listOf("Where is the truth? (1929)", "p. 12"),
            headingPath = emptyList(),
            translationCandidate = null,
        )

        assertEquals("Where is the truth? (1929)", resolveImportText(quote))
    }

    @Test
    fun `resolveImportText returns null when no English text can be found anywhere`() {
        val quote = ParsedQuote(
            text = "Où est la vérité?",
            citations = listOf("C'est là.", "p. 12"),
            headingPath = emptyList(),
            translationCandidate = null,
        )

        assertNull(resolveImportText(quote))
    }

    @Test
    fun `resolveImportText returns null when there are no citations to fall back to`() {
        val quote = ParsedQuote(
            text = "Où est la vérité?",
            citations = emptyList(),
            headingPath = emptyList(),
            translationCandidate = null,
        )

        assertNull(resolveImportText(quote))
    }

    @Test
    fun `resolveImportText prefers the translation candidate when present`() {
        val quote = ParsedQuote(
            text = "Un homme heureux est trop content du présent pour trop penser à l'avenir.",
            citations = listOf("A happy man is too satisfied with the present to dwell too much on the future.", "1890s"),
            headingPath = listOf("1890s"),
            translationCandidate = "A happy man is too satisfied with the present to dwell too much on the future.",
        )

        assertEquals(
            "A happy man is too satisfied with the present to dwell too much on the future.",
            resolveImportText(quote),
        )
    }

    @Test
    fun `resolveImportText falls back to the original text when there is no translation candidate`() {
        val quote = ParsedQuote(
            text = "I have the simplest tastes. I am always satisfied with the best.",
            citations = listOf("Lady Windermere's Fan"),
            headingPath = emptyList(),
            translationCandidate = null,
        )

        assertEquals(
            "I have the simplest tastes. I am always satisfied with the best.",
            resolveImportText(quote),
        )
    }

    @Test
    fun `buildRawPayload always records the original text alongside the translation candidate`() {
        val quote = ParsedQuote(
            text = "Autoritätsdusel ist der größte Feind der Wahrheit.",
            citations = listOf("Blind obedience to authority is the greatest enemy of truth."),
            headingPath = listOf("1901"),
            translationCandidate = "Blind obedience to authority is the greatest enemy of truth.",
        )

        val payload = buildRawPayload(
            resolvedTitle = "Albert Einstein",
            requestedName = "Einstein",
            revisionId = 1L,
            parsedQuote = quote,
        ).jsonObject

        assertEquals(quote.text, payload["originalText"]!!.jsonPrimitive.content)
        assertEquals(quote.translationCandidate, payload["translationCandidate"]!!.jsonPrimitive.content)
    }

    @Test
    fun `buildRawPayload records the original text even without a translation candidate`() {
        val quote = ParsedQuote(
            text = "I have the simplest tastes. I am always satisfied with the best.",
            citations = listOf("Lady Windermere's Fan"),
            headingPath = emptyList(),
            translationCandidate = null,
        )

        val payload = buildRawPayload(
            resolvedTitle = "Oscar Wilde",
            requestedName = "Wilde",
            revisionId = 1L,
            parsedQuote = quote,
        ).jsonObject

        assertEquals(quote.text, payload["originalText"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, payload["translationCandidate"])
    }
}
