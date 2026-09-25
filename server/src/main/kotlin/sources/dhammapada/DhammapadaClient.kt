package no.esotericgames.quotes.server.sources.dhammapada

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.httpclient.externalApiHttpClient

private const val BASE_URL = "https://suttacentral.net/api/bilarasuttas"
private const val REQUEST_INTERVAL_MILLIS = 250L

/**
 * Thin wrapper around SuttaCentral's bilara-data API. All SuttaCentral-supported translations are
 * CC0 by the platform's own policy, which is why this importer only ever requests the "sujato"
 * translator (Bhikkhu Sujato dedicates his translations to the public domain).
 */
class DhammapadaClient(
    private val httpClient: HttpClient = externalApiHttpClient(),
) {
    suspend fun fetchRange(startVerse: Int, endVerse: Int, translator: String = "sujato"): BilaraRangeResponse {
        delay(REQUEST_INTERVAL_MILLIS)
        return httpClient.get("$BASE_URL/dhp$startVerse-$endVerse/$translator").body()
    }
}

@Serializable
data class BilaraRangeResponse(@SerialName("translation_text") val translationText: Map<String, String>)
