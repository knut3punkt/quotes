package no.esotericgames.quotes.server.sources.wikiquote

data class SourceHints(val title: String?, val year: Int?, val location: String?)

// A citation embedding its own work title looks like "<Title> (YYYY)[, <rest>]", e.g. Winston
// Churchill's "The Story of the Malakand Field Force (1898), Chapter III" — real pages sometimes
// nest the actual work title inside a citation rather than the section heading (see below). The
// title group is capped at 80 chars and matched non-greedily from the start of the citation so a
// year buried deep in an unrelated, longer citation (e.g. a biography's own publication year)
// can't be mistaken for the quoted work's year.
private val CITATION_TITLE_YEAR_REGEX = Regex("""^(.{3,80}?)[.,]?\s*\((\d{4})\)\s*[,.]?\s*(.*)$""")

// A section heading that is itself a work title, e.g. "The Birth of Tragedy (1872)".
private val HEADING_TITLE_YEAR_REGEX = Regex("""^(.+?)\s*\((\d{4})\)$""")

// Headings that are never a work title: decade labels ("1900s"), life-period ranges ("Early career
// (1897-1929)"), and Wikiquote's standard non-work top-level section names.
private val GENERIC_HEADING_NAMES = setOf(
    "quotes", "sourced", "attributed", "misattributed", "disputed", "undated", "unsourced",
    "external links", "see also",
)
private val DECADE_HEADING_REGEX = Regex("""^\d{4}s$""")
private val YEAR_RANGE_IN_PARENS_REGEX = Regex("""\(\s*\d{4}\s*[-–—]""")

private val PAGE_LOCATION_REGEX = Regex("""\bpp?\.\s*\d+(?:\s*[-–]\s*\d+)?""", RegexOption.IGNORE_CASE)
private val VERSE_LOCATION_REGEX = Regex("""^[IVXLCDM]+,\s*\d+(?:[-–]\d+)?""")

/**
 * Best-effort guesses for a quote's source title/year/location, for the admin reviewer to accept
 * or correct rather than retype from scratch. Citations are a flat, unstructured list of nested
 * bullet text with no consistent shape (see [parseQuoteSectionHtml]), so this deliberately returns
 * `null` fields rather than forcing a guess whenever the signal is ambiguous.
 */
fun extractSourceHints(
    sectionPath: List<String>,
    citations: List<String>,
    consumedTranslationCitation: String?,
): SourceHints {
    val remaining = citations.filterNot { it == consumedTranslationCitation }

    val citationMatch = remaining.firstNotNullOfOrNull { citation ->
        CITATION_TITLE_YEAR_REGEX.find(citation)?.let { citation to it }
    }

    val title: String?
    val year: Int?
    val locationCandidates: List<String>

    if (citationMatch != null) {
        val (matchedCitation, match) = citationMatch
        title = match.groupValues[1].trim().trim('\'', '"').ifBlank { null }
        year = match.groupValues[2].toIntOrNull()
        val remainder = match.groupValues[3].trim().trim(',', '.', ' ').ifBlank { null }
        locationCandidates = remaining.map { if (it == matchedCitation) remainder else it }.filterNotNull()
    } else {
        val headingTitle = sectionPath.asReversed().firstOrNull { !isGenericHeading(it) }
        val headingMatch = headingTitle?.let { HEADING_TITLE_YEAR_REGEX.find(it) }
        title = (headingMatch?.groupValues?.get(1) ?: headingTitle)?.trim()?.ifBlank { null }
        year = headingMatch?.groupValues?.get(2)?.toIntOrNull()
        locationCandidates = remaining
    }

    val location = locationCandidates.firstOrNull { PAGE_LOCATION_REGEX.containsMatchIn(it) }
        ?: locationCandidates.firstOrNull { VERSE_LOCATION_REGEX.containsMatchIn(it) }
        ?: locationCandidates.singleOrNull()

    return SourceHints(title = title, year = year, location = location)
}

private fun isGenericHeading(heading: String): Boolean {
    val trimmed = heading.trim()
    if (trimmed.lowercase() in GENERIC_HEADING_NAMES) return true
    if (trimmed.lowercase().startsWith("quotes about")) return true
    if (DECADE_HEADING_REGEX.matches(trimmed)) return true
    if (YEAR_RANGE_IN_PARENS_REGEX.containsMatchIn(trimmed)) return true
    return false
}
