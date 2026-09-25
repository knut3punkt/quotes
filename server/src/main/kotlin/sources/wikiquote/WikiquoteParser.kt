package no.esotericgames.quotes.server.sources.wikiquote

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

data class ParsedQuote(
    val text: String,
    val citations: List<String>,
    val headingPath: List<String>,
    val translationCandidate: String?,
)

// h2 is deliberately excluded: it's the section's own title (e.g. "Quotes"/"Attributed"), already
// captured separately as source_confidence, so including it would just prefix every heading path.
private val HEADING_LEVELS = mapOf("h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6)

// Wikiquote has no structural marker distinguishing a plain-prose translation from a real source
// citation among a quote's nested bullets, so this is a best-effort hint for a human reviewer, not
// a guarantee. A nested entry is treated as citation-like (and so *not* a translation candidate) if
// it links/italicizes a source, or its text carries a year or a common citation phrase.
private val CITATION_YEAR_REGEX = Regex("""\b(1[5-9]\d{2}|20\d{2})\b""")
private val CITATION_PAGE_REGEX = Regex("""^pp?\.?\s*\d+""", RegexOption.IGNORE_CASE)
private val CITATION_KEYWORDS = listOf(
    "quoted in", "quoted by", "reported in", "letter to", "letter from", "interview",
    "variant:", "as quoted", "as translated", "translated by", "translation of", "source:",
)

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
    val citationElements = li.select("ul li")
    val citations = citationElements.map { ownTextExcludingNestedLists(it) }.filter { it.isNotEmpty() }

    val text = ownTextExcludingNestedLists(li)
    if (text.isEmpty()) return null

    val translationCandidate = if (looksNonEnglish(text)) {
        citationElements
            .firstOrNull { !looksLikeCitation(it) }
            ?.let { ownTextExcludingNestedLists(it) }
            ?.takeIf { it.isNotEmpty() }
    } else {
        null
    }

    return ParsedQuote(text = text, citations = citations, headingPath = headingPath, translationCandidate = translationCandidate)
}

/** Full rendered text of [element] (including inline links/formatting), minus any nested `<ul>`. */
private fun ownTextExcludingNestedLists(element: Element): String {
    val clone = element.clone()
    clone.select("ul").forEach { it.remove() }
    return clone.text().trim()
}

// U+FB00-FB06 (ﬀ ﬁ ﬂ ﬃ ﬄ ﬅ ﬆ) are typographic ligatures from how the book text was digitized, not a
// language signal — an all-English quote containing "ﬁt" for "fit" must not count as non-English.
/** True for accented Latin or non-Latin letters; ignores decorative punctuation like Wikiquote's ❝❞. */
private fun looksNonEnglish(text: String): Boolean =
    text.any { it.isLetter() && it.code > 127 && it.code !in 0xFB00..0xFB06 }

// Nested `<ul>` is stripped first (same as ownTextExcludingNestedLists) so a deeper sub-citation's
// link/year/italic markup doesn't bleed up and wrongly mark an outer, plain-prose translation entry
// as citation-like (seen for real on triple-nested entries, e.g. Goethe's "Der Erlkönig").
private fun looksLikeCitation(element: Element): Boolean {
    val clone = element.clone()
    clone.select("ul").forEach { it.remove() }
    if (clone.selectFirst("a") != null || clone.selectFirst("i") != null) return true
    val text = clone.text().lowercase()
    if (CITATION_YEAR_REGEX.containsMatchIn(text)) return true
    if (CITATION_PAGE_REGEX.containsMatchIn(text)) return true
    return CITATION_KEYWORDS.any { text.contains(it) }
}

/**
 * Resolves the English text to stage for [parsedQuote], or `null` when none can be found — the
 * scope is English-only quotes, so an unresolved non-English quote must be skipped at staging
 * rather than imported untranslated. Order: the primary translation-candidate heuristic (see
 * [toParsedQuote]); else, if the quote text is non-English, the first listed citation as a
 * last-resort guess (some pages list a plain-prose translation after citation-shaped bullets the
 * primary heuristic didn't consider); else the quote text itself, when it's already English. A
 * final non-English check catches a still-wrong guess in either fallback.
 */
internal fun resolveImportText(parsedQuote: ParsedQuote): String? {
    val resolved = when {
        parsedQuote.translationCandidate != null -> parsedQuote.translationCandidate
        looksNonEnglish(parsedQuote.text) -> parsedQuote.citations.firstOrNull() ?: parsedQuote.text
        else -> parsedQuote.text
    }
    return resolved.takeUnless { looksNonEnglish(it) }
}
