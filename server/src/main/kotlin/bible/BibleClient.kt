package no.esotericgames.quotes.server.bible

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.httpclient.externalApiHttpClient
import no.esotericgames.quotes.server.httpclient.getWithRetryAfter

private const val BASE_URL = "https://bible-api.com"

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
    private val httpClient: HttpClient = externalApiHttpClient(),
) {
    suspend fun fetchReference(reference: String, translation: String): BibleReferenceResponse? {
        delay(REQUEST_INTERVAL_MILLIS)
        val response = httpClient.getWithRetryAfter(
            "$BASE_URL/${reference.replace(" ", "+")}",
            DEFAULT_RETRY_AFTER_SECONDS,
        ) {
            parameter("translation", translation)
        }
        if (response.status != HttpStatusCode.OK) return null
        return response.body()
    }
}

@Serializable
data class BibleReferenceResponse(
    val reference: String,
    val text: String,
    @SerialName("translation_id") val translationId: String,
    @SerialName("translation_name") val translationName: String,
)
