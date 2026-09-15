package no.esotericgames.quotes.server.importing

import java.security.MessageDigest

// Straight-quote/dash variants so the same underlying quote normalizes identically no matter which
// source's typography it arrives with (Wikiquote's curly quotes vs. a plain-text scripture dump).
private val APOSTROPHE_VARIANTS = Regex("[‘’‚‛]")
private val DOUBLE_QUOTE_VARIANTS = Regex("[“”„‟]")
private val DASH_VARIANTS = Regex("[‐‑‒–—―]")
private val WHITESPACE_RUN = Regex("\\s+")
private val TRAILING_PUNCTUATION_AND_SPACE = Regex("[.,;:!?\"'\\-\\s]+$")

/**
 * Normalizes quote text for cross-source duplicate detection (see [no.esotericgames.quotes.server.importing.stageQuote]).
 * Mirrors the SQL `normalize_quote_text` function used to backfill existing rows in
 * `V7__add_text_normalization_and_dedup.sql` — keep the two in sync if either changes.
 */
fun normalizeQuoteText(text: String): String {
    var result = text
    result = result.replace(APOSTROPHE_VARIANTS, "'")
    result = result.replace(DOUBLE_QUOTE_VARIANTS, "\"")
    result = result.replace(DASH_VARIANTS, "-")
    result = result.lowercase()
    result = result.replace(WHITESPACE_RUN, " ").trim()
    result = result.replace(TRAILING_PUNCTUATION_AND_SPACE, "")
    return result
}

/** Normalizes an author name for dedup lookups against `authors.normalized_name`. */
fun normalizeAuthorName(name: String): String =
    name.trim().replace(WHITESPACE_RUN, " ").lowercase()

fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
