package no.esotericgames.quotes.server.extraction

/**
 * Tunes how aggressively long sentences get sub-split into clause-level units. Unrelated to
 * [ExtractionPolicy]'s excerpt length/score thresholds — this only controls unit granularity.
 */
data class SegmentationPolicy(val maxSentenceWordsBeforeClauseSplit: Int = 45)

private typealias Span = Pair<Int, Int> // startInclusive, endExclusive

private val ABBREVIATIONS = setOf(
    "mr", "mrs", "ms", "dr", "prof", "st", "vs", "etc", "e.g", "i.e", "sr", "jr",
)

private val SENTENCE_BOUNDARY_REGEX = Regex("""[.!?]+["')\]]*""")
private val CLAUSE_BOUNDARY_REGEX = Regex("""[;:]|—|--""")
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
    val precedingWord = source.substring(wordStart, punctuationStart).trimEnd('.', '!', '?', '\'', '"', ')', ']')
    if (precedingWord.length == 1 && precedingWord[0].isUpperCase()) return false // an initial, e.g. "J."
    if (precedingWord.lowercase() in ABBREVIATIONS) return false

    var next = afterPunctuation
    while (next < source.length && source[next].isWhitespace()) next++
    if (next >= source.length) return true
    val nextChar = source[next]
    return nextChar.isUpperCase() || nextChar == '"' || nextChar == '\'' || nextChar == '“'
}

private fun splitLongSpanAtClauseBoundaries(source: String, span: Span): List<Span> {
    val (spanStart, spanEnd) = span
    val matches = CLAUSE_BOUNDARY_REGEX.findAll(source, spanStart).takeWhile { it.range.first < spanEnd }.toList()
    if (matches.isEmpty()) return listOf(span)

    val subSpans = mutableListOf<Span>()
    var start = spanStart
    for (match in matches) {
        val end = match.range.last + 1 // keep the punctuation attached to the preceding sub-unit
        if (end > start) {
            val trimmed = trimSpan(source, start to end)
            if (trimmed.second > trimmed.first) subSpans += trimmed
        }
        start = end
    }
    if (start < spanEnd) {
        val trimmed = trimSpan(source, start to spanEnd)
        if (trimmed.second > trimmed.first) subSpans += trimmed
    }
    return subSpans.ifEmpty { listOf(span) }
}
