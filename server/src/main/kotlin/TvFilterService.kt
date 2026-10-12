package no.esotericgames.quotes.server

import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.http.parseQueryString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.admin.QuoteFilter
import no.esotericgames.quotes.server.admin.quoteFilterFrom
import no.esotericgames.quotes.server.db.QuoteQualityRuns
import no.esotericgames.quotes.server.db.TvQuoteFilters
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

/** Paging parameters of the admin list, which mean nothing in a stored filter. */
private val PAGING_PARAMETERS = setOf("page", "pageSize")

@Serializable
data class TvFilterRequest(val query: String)

/** The active TV preview filter, with how many quotes the TV can show under it. */
@Serializable
data class TvFilterResponse(
    /** The admin quote list's filter parameters, as a URL query string. */
    val query: String,
    val updatedAt: String,
    /** The label of the filter's quality run, when it has one. */
    val qualityRunLabel: String?,
    val poolQuotes: Long,
    val poolAsExcerpt: Long,
)

/**
 * The TV preview filter (docs/features/quote-quality.md, "TV preview filter"): an approved-quotes filter, set
 * from the admin, that `GET /api/quotes/random` honours until it is cleared. It changes nothing else.
 */
class TvFilterService(private val publicQuoteService: PublicQuoteService) {

    suspend fun get(): TvFilterResponse? = withContext(Dispatchers.IO) {
        suspendTransaction { storedRow()?.let { (query, updatedAt) -> response(query, updatedAt) } }
    }

    /** Validates [query] as quote-list filter parameters and makes it the TV filter. */
    suspend fun set(query: String): TvFilterResponse = withContext(Dispatchers.IO) {
        val normalized = normalizeQuery(query)
        val filter = parseTvQuoteFilter(normalized)
        suspendTransaction {
            filter.qualityRunId?.let { runId ->
                require(!QuoteQualityRuns.selectAll().where { QuoteQualityRuns.id eq runId }.empty()) {
                    "quality run $runId does not exist"
                }
            }
            TvQuoteFilters.upsert {
                it[id] = TvQuoteFilters.SINGLE_ROW_ID
                it[TvQuoteFilters.query] = normalized
                it[updatedAt] = OffsetDateTime.now()
            }
            response(normalized, storedRow()!!.second)
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) { suspendTransaction { TvQuoteFilters.deleteAll() } }
    }

    private fun response(query: String, updatedAt: OffsetDateTime): TvFilterResponse {
        val filter = parseTvQuoteFilter(query)
        val pool = publicQuoteService.poolSize(filter)
        val runLabel = filter.qualityRunId?.let { runId ->
            QuoteQualityRuns.selectAll().where { QuoteQualityRuns.id eq runId }.singleOrNull()?.get(QuoteQualityRuns.label)
        }
        return TvFilterResponse(
            query = query,
            updatedAt = updatedAt.toString(),
            qualityRunLabel = runLabel,
            poolQuotes = pool.quotes,
            poolAsExcerpt = pool.asExcerpt,
        )
    }
}

private fun storedRow(): Pair<String, OffsetDateTime>? =
    TvQuoteFilters.selectAll().singleOrNull()?.let { it[TvQuoteFilters.query] to it[TvQuoteFilters.updatedAt] }

/** [query] without paging parameters, re-encoded, so equal filters are stored alike. */
internal fun normalizeQuery(query: String): String {
    val params = parseQueryString(query.removePrefix("?"))
    return Parameters.build {
        params.entries().filter { (name, _) -> name !in PAGING_PARAMETERS }.forEach { (name, values) -> appendAll(name, values) }
    }.formUrlEncode()
}

/** Parses a stored TV filter with the admin quote list's rules; throws [IllegalArgumentException] when invalid. */
internal fun parseTvQuoteFilter(query: String): QuoteFilter = quoteFilterFrom(parseQueryString(query), defaultPageSize = 1)

/**
 * The TV preview filter in effect, or null when none is set. A stored filter that no longer parses (the filter
 * parameters changed since it was saved) is ignored rather than breaking the TV. Must run inside a transaction.
 */
fun activeTvQuoteFilter(): QuoteFilter? = storedRow()?.let { (query, _) ->
    runCatching { parseTvQuoteFilter(query) }
        .onFailure { logger.warn("ignoring a TV filter that no longer parses: {}", query, it) }
        .getOrNull()
}

private val logger = LoggerFactory.getLogger("no.esotericgames.quotes.server.TvFilterService")
