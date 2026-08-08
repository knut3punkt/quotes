package no.esotericgames.quotes.server.wikiquote

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

data class ParsedQuote(
    val text: String,
    val citations: List<String>,
    val headingPath: List<String>,
)

// h2 is deliberately excluded: it's the section's own title (e.g. "Quotes"/"Attributed"), already
// captured separately as source_confidence, so including it would just prefix every heading path.
private val HEADING_LEVELS = mapOf("h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6)

/**
 * Parses the rendered HTML of one Wikiquote section (from `action=parse&prop=text`) into a flat
 * list of quotes. Walks the whole subtree (not just direct children) so wrapper elements from
 * templates like multi-column lists don't hide headings or bullet lists; top-level `<ul>`s are
 * consumed whole (including any nested `<ul>`) so nested lists are never double-visited.
 */
fun parseQuoteSectionHtml(html: String): List<ParsedQuote> {
    val document = Jsoup.parseBodyFragment(html)
    val root = document.body().selectFirst("div.mw-parser-output") ?: document.body()

    val quotes = mutableListOf<ParsedQuote>()
    val headingStack = ArrayDeque<Pair<Int, String>>()

    NodeTraversor.filter(
        object : NodeFilter {
            override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
                if (node !is Element) return NodeFilter.FilterResult.CONTINUE

                val headingLevel = HEADING_LEVELS[node.tagName()]
                if (headingLevel != null) {
                    while (headingStack.isNotEmpty() && headingStack.last().first >= headingLevel) {
                        headingStack.removeLast()
                    }
                    headingStack.addLast(headingLevel to node.text().trim())
                    return NodeFilter.FilterResult.CONTINUE
                }

                if (node.tagName() == "ul") {
                    val headingPath = headingStack.map { it.second }
                    node.children()
                        .filter { it.tagName() == "li" }
                        .mapNotNull { toParsedQuote(it, headingPath) }
                        .forEach { quotes += it }
                    return NodeFilter.FilterResult.SKIP_CHILDREN
                }

                return NodeFilter.FilterResult.CONTINUE
            }

            override fun tail(node: Node, depth: Int) = NodeFilter.FilterResult.CONTINUE
        },
        root,
    )

    return quotes
}

private fun toParsedQuote(li: Element, headingPath: List<String>): ParsedQuote? {
    val citations = li.select("ul li")
        .map { citationLi -> ownTextExcludingNestedLists(citationLi) }
        .filter { it.isNotEmpty() }

    val text = ownTextExcludingNestedLists(li)

    if (text.isEmpty()) return null
    return ParsedQuote(text = text, citations = citations, headingPath = headingPath)
}

/** Full rendered text of [element] (including inline links/formatting), minus any nested `<ul>`. */
private fun ownTextExcludingNestedLists(element: Element): String {
    val clone = element.clone()
    clone.select("ul").forEach { it.remove() }
    return clone.text().trim()
}
