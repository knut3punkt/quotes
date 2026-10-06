package no.esotericgames.quotes.server.admin

import io.ktor.http.Parameters
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Filter keys for an imported quote's author and source, as sent by the admin's multi-select filters.
 * `none` matches a missing value; other keys carry a prefix so they can never collide with it.
 */
internal const val NONE_FILTER_KEY = "none"
private const val AUTHOR_NAME_PREFIX = "name:"
private const val ID_PREFIX = "id:"
private const val SUGGESTED_SOURCE_PREFIX = "raw:"

sealed interface AuthorKey {
    /** No raw author, or a blank one. */
    data object None : AuthorKey

    /** A raw author, compared after trimming. */
    data class Name(val name: String) : AuthorKey
}

/** A nullable reference on approved quotes (author, source): `id:N`, or `none` for no reference. */
sealed interface IdKey {
    data object None : IdKey

    data class Id(val id: Int) : IdKey
}

sealed interface SourceKey {
    /** Neither a linked source nor a suggested source title. */
    data object None : SourceKey

    data class Linked(val sourceId: Int) : SourceKey

    /** No linked source, but the import suggested this (trimmed) title. */
    data class Suggested(val title: String) : SourceKey
}

fun authorKeyOf(rawAuthor: String?): String =
    rawAuthor?.trim()?.takeIf { it.isNotEmpty() }?.let { AUTHOR_NAME_PREFIX + it } ?: NONE_FILTER_KEY

fun sourceKeyOf(sourceId: Int?, rawSourceTitle: String?): String = when {
    sourceId != null -> ID_PREFIX + sourceId
    else -> rawSourceTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { SUGGESTED_SOURCE_PREFIX + it } ?: NONE_FILTER_KEY
}

fun idKeyOf(id: Int?): String = id?.let { ID_PREFIX + it } ?: NONE_FILTER_KEY

fun parseIdKey(key: String): IdKey = when {
    key == NONE_FILTER_KEY -> IdKey.None
    key.startsWith(ID_PREFIX) -> IdKey.Id(
        key.removePrefix(ID_PREFIX).toIntOrNull() ?: throw IllegalArgumentException("unknown id filter key '$key'"),
    )
    else -> throw IllegalArgumentException("unknown id filter key '$key'")
}

fun parseAuthorKey(key: String): AuthorKey = when {
    key == NONE_FILTER_KEY -> AuthorKey.None
    key.startsWith(AUTHOR_NAME_PREFIX) && key.length > AUTHOR_NAME_PREFIX.length ->
        AuthorKey.Name(key.removePrefix(AUTHOR_NAME_PREFIX))
    else -> throw IllegalArgumentException("unknown author filter key '$key'")
}

fun parseSourceKey(key: String): SourceKey = when {
    key == NONE_FILTER_KEY -> SourceKey.None
    key.startsWith(ID_PREFIX) -> SourceKey.Linked(
        key.removePrefix(ID_PREFIX).toIntOrNull()
            ?: throw IllegalArgumentException("unknown source filter key '$key'"),
    )
    key.startsWith(SUGGESTED_SOURCE_PREFIX) && key.length > SUGGESTED_SOURCE_PREFIX.length ->
        SourceKey.Suggested(key.removePrefix(SUGGESTED_SOURCE_PREFIX))
    else -> throw IllegalArgumentException("unknown source filter key '$key'")
}

private fun parseTimestamp(name: String, value: String): OffsetDateTime =
    try {
        OffsetDateTime.parse(value)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("$name must be an ISO-8601 timestamp with offset", e)
    }

private fun parsePossibleDuplicate(value: String): Boolean = when (value) {
    "flagged" -> true
    "notFlagged" -> false
    else -> throw IllegalArgumentException("unknown possibleDuplicate filter '$value'")
}

/** Filters shared by `GET /admin/imported-quotes` and `GET /admin/imported-quotes/selection`. */
fun importedQuoteFilterFrom(params: Parameters, defaultPageSize: Int): ImportedQuoteFilter = ImportedQuoteFilter(
    statuses = params.getAll("status")?.toSet(),
    provider = params["provider"],
    sourceConfidence = params["sourceConfidence"],
    search = params["search"],
    authors = params.getAll("author").orEmpty().map(::parseAuthorKey).toSet(),
    sources = params.getAll("source").orEmpty().map(::parseSourceKey).toSet(),
    possibleDuplicate = params["possibleDuplicate"]?.let(::parsePossibleDuplicate),
    importedFrom = params["importedFrom"]?.let { parseTimestamp("importedFrom", it) },
    importedBefore = params["importedBefore"]?.let { parseTimestamp("importedBefore", it) },
    minLength = params["minLength"]?.toIntOrNull(),
    maxLength = params["maxLength"]?.toIntOrNull(),
    page = params["page"]?.toIntOrNull() ?: 1,
    pageSize = params["pageSize"]?.toIntOrNull() ?: defaultPageSize,
)

/** Filters shared by `GET /admin/quotes` and `GET /admin/quotes/selection`. */
fun quoteFilterFrom(params: Parameters, defaultPageSize: Int): QuoteFilter = QuoteFilter(
    authors = params.getAll("author").orEmpty().map(::parseIdKey).toSet(),
    sources = params.getAll("source").orEmpty().map(::parseIdKey).toSet(),
    languages = params.getAll("language").orEmpty().toSet(),
    search = params["search"],
    providers = params.getAll("provider").orEmpty().toSet(),
    sourceConfidences = params.getAll("sourceConfidence").orEmpty().toSet(),
    minLength = params["minLength"]?.toIntOrNull(),
    maxLength = params["maxLength"]?.toIntOrNull(),
    tag = params["tag"],
    excerpts = enrichmentFilters(params, "excerpts"),
    interpretations = enrichmentFilters(params, "interpretations"),
    tags = enrichmentFilters(params, "tags"),
    page = params["page"]?.toIntOrNull() ?: 1,
    pageSize = params["pageSize"]?.toIntOrNull() ?: defaultPageSize,
)

private fun enrichmentFilters(params: Parameters, name: String): Set<EnrichmentFilter> =
    params.getAll(name).orEmpty().map(EnrichmentFilter::fromQueryValue).toSet()

/**
 * Multi-select filter choices from `(key, count)` rows. Rows sharing a key are merged (null and blank values both
 * become the none key), options are sorted by label, and when [noneLabel] is given the none option comes first and is
 * always offered.
 */
internal fun buildFilterOptions(
    counts: List<Pair<String, Long>>,
    noneLabel: String?,
    labelOf: (String) -> String,
): List<FilterOptionResponse> {
    val merged = counts.groupingBy { it.first }.fold(0L) { total, (_, count) -> total + count }
    val options = merged.filterKeys { noneLabel == null || it != NONE_FILTER_KEY }
        .map { (key, count) -> FilterOptionResponse(value = key, label = labelOf(key), count = count) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    if (noneLabel == null) return options
    return listOf(FilterOptionResponse(value = NONE_FILTER_KEY, label = noneLabel, count = merged[NONE_FILTER_KEY] ?: 0)) +
        options
}
