package no.esotericgames.quotes.server.wikiquote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixtures under src/test/resources/wikiquote/ are real `action=parse&prop=text` responses saved
 * from en.wikiquote.org, so these tests exercise the parser against real page markup rather than
 * hand-crafted HTML.
 */
class WikiquoteParserTest {

    @Test
    fun `parses Einstein's Quotes section into a flat list with heading paths`() {
        val quotes = parseFixture("einstein-quotes-section.json")

        assertTrue(quotes.isNotEmpty())
        assertTrue(quotes.all { it.text.isNotBlank() })

        val firstQuote = quotes.first()
        assertTrue(firstQuote.text.contains("Everything should be made simple"))
        assertEquals(emptyList(), firstQuote.headingPath)
        assertEquals(listOf("Repeated throughout his life, see: Quote Investigator"), firstQuote.citations)
        assertNull(firstQuote.translationCandidate, "decorative ❝❞ quote marks shouldn't look non-English")

        val frenchQuote = quotes.first { it.text.startsWith("Un homme heureux") }
        assertEquals(listOf("1890s"), frenchQuote.headingPath)
        assertEquals(2, frenchQuote.citations.size)
        assertEquals(
            "A happy man is too satisfied with the present to dwell too much on the future.",
            frenchQuote.translationCandidate,
        )

        val germanQuote = quotes.first { it.text.startsWith("Autoritätsdusel") }
        assertEquals(
            "Blind obedience to authority is the greatest enemy of truth.",
            germanQuote.translationCandidate,
        )
    }

    @Test
    fun `parses Wilde's Attributed section, one citation per quote, no subheadings`() {
        val quotes = parseFixture("wilde-attributed-section.json")

        assertEquals(4, quotes.size)
        assertTrue(quotes.all { it.headingPath.isEmpty() })
        assertTrue(quotes.all { it.citations.size == 1 })
        assertTrue(quotes.any { it.text.startsWith("I have the simplest tastes.") })
        assertTrue(quotes.all { it.translationCandidate == null }, "all-English quotes shouldn't get a translation candidate")
    }

    @Test
    fun `translation candidate detection ignores markup from a deeper nested citation`() {
        // Real triple-nested structure from Goethe's page: quote -> translation -> the
        // translation's own sub-citation (linked, italic, with a year). A naive check of the
        // translation li's full descendant text/links would wrongly see the sub-citation's <a>,
        // <i> and "(1782)" and misclassify the translation itself as citation-like.
        val html = """
            <div class="mw-parser-output">
            <ul><li><i>Wer reitet so spät durch Nacht und Wind?</i>
            <ul><li><b>Who rides, so late, through night and wind?</b>
            <ul><li><i><a href="https://en.wikipedia.org/wiki/Der_Erlk%C3%B6nig">Der Erlkönig</a></i> (1782)</li></ul>
            </li></ul>
            </li></ul>
            </div>
        """.trimIndent()

        val quotes = parseQuoteSectionHtml(html)

        assertEquals(1, quotes.size)
        assertEquals("Who rides, so late, through night and wind?", quotes.first().translationCandidate)
    }

    private fun parseFixture(fileName: String): List<ParsedQuote> {
        val json = File("src/test/resources/wikiquote/$fileName").readText()
        val html = Json.parseToJsonElement(json).jsonObject["parse"]!!.jsonObject["text"]!!.jsonPrimitive.content
        return parseQuoteSectionHtml(html)
    }
}
