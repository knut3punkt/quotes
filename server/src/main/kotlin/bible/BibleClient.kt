package no.esotericgames.quotes.server.bible

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://bible-api.com"
private const val USER_AGENT = "TVQuotes-Importer/1.0 (contact: knut3punkt@gmail.com)"

// bible-api.com documents a 15-requests-per-30-seconds limit; 2100ms keeps every run comfortably
// under that (~14.3 req/30s) instead of tripping it partway through a seed-list import.
private const val REQUEST_INTERVAL_MILLIS = 2100L
private const val DEFAULT_RETRY_AFTER_SECONDS = 30L

/**
 * Thin wrapper around bible-api.com's human-readable reference lookup (e.g. "John 3:16",
 * "Ecclesiastes 3:1-8"). Every translation the API serves self-declares its license in `/data`;
 * this client is only ever pointed at "kjv", which that manifest marks Public Domain.
 */
class BibleClient(
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(UserAgent) {
            agent = USER_AGENT
        }
    },
) {
    suspend fun fetchReference(reference: String, translation: String): BibleReferenceResponse? {
        val response = apiGet(reference, translation)
        if (response.status != HttpStatusCode.OK) return null
        return response.body()
    }

    private suspend fun apiGet(reference: String, translation: String): HttpResponse {
        delay(REQUEST_INTERVAL_MILLIS)
        val response = get(reference, translation)
        if (response.status != HttpStatusCode.TooManyRequests) return response
        val retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS
        delay(retryAfterSeconds * 1000)
        return get(reference, translation)
    }

    private suspend fun get(reference: String, translation: String): HttpResponse =
        httpClient.get("$BASE_URL/${reference.replace(" ", "+")}") {
            parameter("translation", translation)
        }
}

@Serializable
data class BibleReferenceResponse(
    val reference: String,
    val text: String,
    @SerialName("translation_id") val translationId: String,
    @SerialName("translation_name") val translationName: String,
)
