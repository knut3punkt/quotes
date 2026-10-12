package no.esotericgames.quotes.server.quality

/** Cheap text checks computed by the server, never by a model, so they can be compared with the model's problems. */
enum class PrecheckFlag {
    OVER_WORD_LIMIT,
    VERY_SHORT,
    LOWERCASE_START,
    TRAILING_CONTINUATION,
    LIST_FRAGMENT,
    BRACKETED_INSERTION,
    ELLIPSIS,
    UNBALANCED_QUOTES,
    EDITORIAL_PREFIX,
    CITATION_ONLY,
    LIKELY_NON_ENGLISH,
}

private val WHITESPACE_REGEX = Regex("""\s+""")
private val WORD_REGEX = Regex("""\p{L}+""")
private val BRACKETED_INSERTION_REGEX = Regex("""\[[^\]]*]""")
private val ELLIPSIS_REGEX = Regex("""\.\s?\.\s?\.|…""")
private val LIST_FRAGMENT_START_REGEX = Regex("""^\s*([*•\-–]|\d+[.)])\s""")
private val EDITORIAL_PREFIX_REGEX = Regex(
    """^\W*(variant( translation)?|alternate translation|alternative translation|translation|original|note|""" +
        """as quoted in|quoted in|attributed|misattributed|paraphrased|disputed|unsourced)\b""",
    RegexOption.IGNORE_CASE,
)
private val CITATION_REGEX = Regex(
    """\b(bk|ch|chap|vol|sec|pp?)\.\s*[\dIVXLC]|§|\b(book|chapter|part|letter|section|act|scene)\s+[\dIVXLC]+\b""",
    RegexOption.IGNORE_CASE,
)
private val CONTINUATION_ENDINGS = setOf(',', ';', ':', '—', '-', '–')
private val TERMINAL_PUNCTUATION = setOf('.', '!', '?', '…')
private val CLOSING_CHARACTERS = setOf('"', '\'', '”', '’', ')', ']')
private const val VERY_SHORT_WORDS = 4
private const val CITATION_ONLY_MAX_WORDS = 10
private const val NON_ENGLISH_MIN_WORDS = 8
private const val MIN_ENGLISH_STOPWORD_RATIO = 0.12

// Common English function words: ordinary English prose is roughly a quarter to a half these.
private val ENGLISH_STOPWORDS = setOf(
    "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "by", "for", "with", "from", "as", "is", "are",
    "was", "were", "be", "been", "it", "its", "that", "this", "which", "who", "what", "not", "no", "he", "she", "they",
    "we", "you", "i", "me", "my", "his", "her", "their", "our", "your", "him", "them", "us", "if", "so", "than", "then",
    "all", "there", "have", "has", "had", "do", "does", "did", "will", "would", "can", "could", "shall", "should",
    "may", "might", "must", "one", "more", "most", "only", "when", "how", "why",
)

/**
 * Deterministic precheck flags for a displayed text (docs/features/quote-quality.md). They are stored
 * next to the model's assessment but never shown to the model, so the two can be measured separately.
 */
object QualityPrechecks {

    fun detect(text: String, language: String, maxFullQuoteWords: Int): List<PrecheckFlag> {
        val trimmed = text.trim()
        val wordCount = countWords(trimmed)
        return buildList {
            if (wordCount > maxFullQuoteWords) add(PrecheckFlag.OVER_WORD_LIMIT)
            if (wordCount < VERY_SHORT_WORDS) add(PrecheckFlag.VERY_SHORT)
            if (trimmed.firstOrNull { it.isLetter() }?.isLowerCase() == true) add(PrecheckFlag.LOWERCASE_START)
            if (endsAsContinuation(trimmed)) add(PrecheckFlag.TRAILING_CONTINUATION)
            if (trimmed.trimEnd { it in CLOSING_CHARACTERS }.endsWith(',') || LIST_FRAGMENT_START_REGEX.containsMatchIn(trimmed)) {
                add(PrecheckFlag.LIST_FRAGMENT)
            }
            if (BRACKETED_INSERTION_REGEX.containsMatchIn(trimmed)) add(PrecheckFlag.BRACKETED_INSERTION)
            if (ELLIPSIS_REGEX.containsMatchIn(trimmed)) add(PrecheckFlag.ELLIPSIS)
            if (hasUnbalancedQuotes(trimmed)) add(PrecheckFlag.UNBALANCED_QUOTES)
            if (EDITORIAL_PREFIX_REGEX.containsMatchIn(trimmed)) add(PrecheckFlag.EDITORIAL_PREFIX)
            if (wordCount <= CITATION_ONLY_MAX_WORDS && CITATION_REGEX.containsMatchIn(trimmed)) add(PrecheckFlag.CITATION_ONLY)
            if (isLikelyNonEnglish(trimmed, language)) add(PrecheckFlag.LIKELY_NON_ENGLISH)
        }
    }

    fun countWords(text: String): Int = text.trim().split(WHITESPACE_REGEX).count { it.isNotEmpty() }

    private fun endsAsContinuation(text: String): Boolean {
        val withoutClosers = text.trimEnd { it in CLOSING_CHARACTERS || it.isWhitespace() }
        val last = withoutClosers.lastOrNull() ?: return false
        return last in CONTINUATION_ENDINGS || (last !in TERMINAL_PUNCTUATION && last.isLetterOrDigit())
    }

    private fun hasUnbalancedQuotes(text: String): Boolean {
        val straightDoubles = text.count { it == '"' }
        val curlyOpen = text.count { it == '“' }
        val curlyClose = text.count { it == '”' }
        return straightDoubles % 2 != 0 || curlyOpen != curlyClose
    }

    private fun isLikelyNonEnglish(text: String, language: String): Boolean {
        if (language != "en") return true
        val words = WORD_REGEX.findAll(text.lowercase()).map { it.value }.toList()
        if (words.size < NON_ENGLISH_MIN_WORDS) return false
        val stopwordRatio = words.count { it in ENGLISH_STOPWORDS }.toDouble() / words.size
        return stopwordRatio < MIN_ENGLISH_STOPWORD_RATIO
    }
}
