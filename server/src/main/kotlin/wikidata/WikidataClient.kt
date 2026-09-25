package no.esotericgames.quotes.server.wikidata

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.esotericgames.quotes.server.httpclient.externalApiHttpClient

private const val WIKIDATA_API_BASE_URL = "https://www.wikidata.org/w/api.php"
private const val REQUEST_INTERVAL_MILLIS = 200L
private const val BIRTH_DATE_PROPERTY = "P569"
private const val DEATH_DATE_PROPERTY = "P570"

// A Wikidata date's "precision" field says how granular it is (9 = year, 10 = month, 11 = day, ...);
// anything coarser (century=7, decade=8, or a legendary figure with only "millennium" precision)
// isn't reliable enough to record as a specific birth/death year.
private const val MIN_YEAR_PRECISION = 9

data class AuthorDates(val birthYear: Int?, val deathYear: Int?)

/** Thin wrapper around Wikidata's public, CC0, unauthenticated API — used only for metadata (this
 * client never fetches quote text). */
class WikidataClient(
    private val httpClient: HttpClient = externalApiHttpClient(),
) {
    /** Best-effort name search; returns the top-ranked entity id, or null if nothing matched. */
    suspend fun searchEntity(name: String): String? {
        delay(REQUEST_INTERVAL_MILLIS)
        val response = httpClient.get(WIKIDATA_API_BASE_URL) {
            parameter("action", "wbsearchentities")
            parameter("search", name)
            parameter("language", "en")
            parameter("type", "item")
            parameter("limit", "1")
            parameter("format", "json")
        }
        return response.body<WikidataSearchResponse>().search.firstOrNull()?.id
    }

    suspend fun fetchDates(qid: String): AuthorDates {
        delay(REQUEST_INTERVAL_MILLIS)
        val response = httpClient.get(WIKIDATA_API_BASE_URL) {
            parameter("action", "wbgetentities")
            parameter("ids", qid)
            parameter("props", "claims")
            parameter("format", "json")
        }
        val entity = response.body<JsonObject>()["entities"]?.jsonObject?.get(qid)?.jsonObject
            ?: return AuthorDates(null, null)
        return AuthorDates(
            birthYear = extractYear(entity, BIRTH_DATE_PROPERTY),
            deathYear = extractYear(entity, DEATH_DATE_PROPERTY),
        )
    }

    private fun extractYear(entity: JsonObject, property: String): Int? {
        val statements = entity["claims"]?.jsonObject?.get(property)?.jsonArray ?: return null
        for (statement in statements) {
            val value = statement.jsonObject["mainsnak"]?.jsonObject
                ?.get("datavalue")?.jsonObject
                ?.get("value")?.jsonObject ?: continue
            val precision = value["precision"]?.jsonPrimitive?.intOrNull ?: continue
            if (precision < MIN_YEAR_PRECISION) continue
            val time = value["time"]?.jsonPrimitive?.content ?: continue
            return parseYear(time)
        }
        return null
    }
}

// Wikidata time values look like "+1875-07-26T00:00:00Z" (CE) or "-0384-01-01T00:00:00Z" (BCE).
private val WIKIDATA_TIME = Regex("""^([+-])(\d+)-\d{2}-\d{2}T""")

internal fun parseYear(wikidataTimeValue: String): Int? {
    val match = WIKIDATA_TIME.find(wikidataTimeValue) ?: return null
    val sign = if (match.groupValues[1] == "-") -1 else 1
    val year = match.groupValues[2].toIntOrNull() ?: return null
    return sign * year
}

@Serializable
private data class WikidataSearchResponse(val search: List<WikidataSearchResult> = emptyList())

@Serializable
private data class WikidataSearchResult(@SerialName("id") val id: String)
