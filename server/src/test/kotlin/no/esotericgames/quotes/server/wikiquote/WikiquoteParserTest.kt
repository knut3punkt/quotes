package no.esotericgames.quotes.server.wikiquote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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

        val frenchQuote = quotes.first { it.text.startsWith("Un homme heureux") }
        assertEquals(listOf("1890s"), frenchQuote.headingPath)
        assertEquals(2, frenchQuote.citations.size)
    }

    @Test
    fun `parses Wilde's Attributed section, one citation per quote, no subheadings`() {
        val quotes = parseFixture("wilde-attributed-section.json")

        assertEquals(4, quotes.size)
        assertTrue(quotes.all { it.headingPath.isEmpty() })
        assertTrue(quotes.all { it.citations.size == 1 })
        assertTrue(quotes.any { it.text.startsWith("I have the simplest tastes.") })
    }

    private fun parseFixture(fileName: String): List<ParsedQuote> {
        val json = File("src/test/resources/wikiquote/$fileName").readText()
        val html = Json.parseToJsonElement(json).jsonObject["parse"]!!.jsonObject["text"]!!.jsonPrimitive.content
        return parseQuoteSectionHtml(html)
    }
}
