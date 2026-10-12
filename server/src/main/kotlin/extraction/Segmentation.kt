package no.esotericgames.quotes.server.extraction

/**
 * Tunes how aggressively long sentences get sub-split into clause-level units. Unrelated to
 * [ExtractionPolicy]'s excerpt length/score thresholds — this only controls unit granularity.
 */
data class SegmentationPolicy(val maxSentenceWordsBeforeClauseSplit: Int = 45)

private typealias Span = Pair<Int, Int> // startInclusive, endExclusive

private val ABBREVIATIONS = setOf(
    "mr", "mrs", "ms", "dr", "prof", "st", "vs", "etc", "e.g", "i.e", "sr", "jr",
    // titles and ranks
    "adm", "gen", "col", "capt", "lt", "sgt", "rev", "hon", "gov", "sen", "rep", "mt", "ft",
    // bibliographic references
    "vol", "ch", "bk", "pp", "cf", "viz", "ca", "trans",
    // scripture books, as quote sources cite them ("Matt. 23:12"). Abbreviations that are also
    // ordinary sentence-final words ("Tim.", "pet.", "Phil.") are left out.
    "lev", "deut", "matt", "mk", "lk", "jn", "rom", "cor", "eph", "thess",
    "heb", "jas", "ps", "prov", "eccl", "isa", "jer", "ezek",
)

private val SENTENCE_BOUNDARY_REGEX = Regex("""[.!?]+["')\]]*""")

// A colon only counts when followed by whitespace, so a reference like "23:12" or a time like "3:30"
// is never split.
private val CLAUSE_BOUNDARY_REGEX = Regex(""";|:(?=\s)|—|--""")

// Initials written without spaces, such as "C.S" or "W.K" (the final period is trimmed first).
private val INITIALS_REGEX = Regex("""^(\p{L}\.)+\p{L}$""")
private const val LEADING_WORD_PUNCTUATION = "([\"'“‘—-"
private val WHITESPACE_REGEX = Regex("""\s+""")

/**
 * Divides a source quote into ordered, stable-offset units: primarily at sentence boundaries, with
 * long sentences additionally split at strong clause punctuation (semicolon, colon, em dash), per
 * docs/features/quote-extraction.md. Comma-splitting is never used. Offsets always point into the
 * original, unmodified [source] string. This is a best-effort heuristic (no NLP library is used) —
 * mis-segmentation only affects candidate quality, never data integrity, since excerpts are always
 * reconstructed from these same offsets against the original source.
 */
fun segmentSourceIntoUnits(source: String, policy: SegmentationPolicy = SegmentationPolicy()): List<SourceUnit> {
    val sentenceSpans = splitIntoSentenceSpans(source)
    val unitSpans = sentenceSpans.flatMap { span ->
        val wordCount = countWords(source.substring(span.first, span.second))
        if (wordCount > policy.maxSentenceWordsBeforeClauseSplit) {
            splitLongSpanAtClauseBoundaries(source, span)
        } else {
            listOf(span)
        }
    }
    return unitSpans.mapIndexed { index, span ->
        SourceUnit(id = index + 1, startOffset = span.first, endOffset = span.second, text = source.substring(span.first, span.second))
    }
}

private fun countWords(text: String): Int = text.trim().split(WHITESPACE_REGEX).count { it.isNotEmpty() }

private fun trimSpan(source: String, span: Span): Span {
    var (start, end) = span
    while (start < end && source[start].isWhitespace()) start++
    while (end > start && source[end - 1].isWhitespace()) end--
    return start to end
}

private fun splitIntoSentenceSpans(source: String): List<Span> {
    if (source.isBlank()) return emptyList()

    val boundaryEnds = mutableListOf<Int>()
    var searchFrom = 0
    while (true) {
        val match = SENTENCE_BOUNDARY_REGEX.find(source, searchFrom) ?: break
        val matchEnd = match.range.last + 1
        searchFrom = matchEnd
        if (isGenuineSentenceBoundary(source, match.range.first, matchEnd)) {
            boundaryEnds += matchEnd
        }
    }
    if (boundaryEnds.isEmpty() || boundaryEnds.last() != source.length) {
        boundaryEnds += source.length
    }

    val spans = mutableListOf<Span>()
    var start = 0
    for (end in boundaryEnds) {
        if (end > start) {
            val trimmed = trimSpan(source, start to end)
            if (trimmed.second > trimmed.first) spans += trimmed
        }
        start = end
    }
    return spans
}

private fun isGenuineSentenceBoundary(source: String, punctuationStart: Int, afterPunctuation: Int): Boolean {
    var wordStart = punctuationStart
    while (wordStart > 0 && !source[wordStart - 1].isWhitespace()) wordStart--
    val precedingWord = source.substring(wordStart, punctuationStart)
        .trimStart { it in LEADING_WORD_PUNCTUATION }
        .trimEnd('.', '!', '?', '\'', '"', ')', ']')
    if (precedingWord.length == 1 && precedingWord[0].isUpperCase()) return false // an initial, e.g. "J."
    if (INITIALS_REGEX.matches(precedingWord)) return false // initials without spaces, e.g. "C.S."
    if (precedingWord.lowercase() in ABBREVIATIONS) return false

    var next = afterPunctuation
    while (next < source.length && source[next].isWhitespace()) next++
    if (next >= source.length) return true
    val nextChar = source[next]
    return nextChar.isUpperCase() || nextChar == '"' || nextChar == '\'' || nextChar == '“'
}

/**
 * The clause punctuation itself falls in the gap between two units, belonging to neither. A range
 * that spans the boundary still contains it verbatim (excerpts are reconstructed from the first
 * unit's start to the last unit's end), while a range that ends at the boundary no longer ends on a
 * dangling ";" or "—".
 */
private fun splitLongSpanAtClauseBoundaries(source: String, span: Span): List<Span> {
    val (spanStart, spanEnd) = span
    val matches = CLAUSE_BOUNDARY_REGEX.findAll(source, spanStart).takeWhile { it.range.first < spanEnd }.toList()
    if (matches.isEmpty()) return listOf(span)

    val subSpans = mutableListOf<Span>()
    var start = spanStart
    for (match in matches) {
        val end = match.range.first
        if (end > start) {
            val trimmed = trimSpan(source, start to end)
            if (trimmed.second > trimmed.first) subSpans += trimmed
        }
        start = match.range.last + 1
    }
    if (start < spanEnd) {
        val trimmed = trimSpan(source, start to spanEnd)
        if (trimmed.second > trimmed.first) subSpans += trimmed
    }
    return subSpans.ifEmpty { listOf(span) }
}
