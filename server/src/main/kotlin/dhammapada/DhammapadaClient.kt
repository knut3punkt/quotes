package no.esotericgames.quotes.server.dhammapada

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://suttacentral.net/api/bilarasuttas"
private const val USER_AGENT = "TVQuotes-Importer/1.0 (contact: knut3punkt@gmail.com)"
private const val REQUEST_INTERVAL_MILLIS = 250L

/**
 * Thin wrapper around SuttaCentral's bilara-data API. All SuttaCentral-supported translations are
 * CC0 by the platform's own policy, which is why this importer only ever requests the "sujato"
 * translator (Bhikkhu Sujato dedicates his translations to the public domain).
 */
class DhammapadaClient(
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(UserAgent) {
            agent = USER_AGENT
        }
    },
) {
    suspend fun fetchRange(startVerse: Int, endVerse: Int, translator: String = "sujato"): BilaraRangeResponse {
        delay(REQUEST_INTERVAL_MILLIS)
        return httpClient.get("$BASE_URL/dhp$startVerse-$endVerse/$translator").body()
    }
}

@Serializable
data class BilaraRangeResponse(@SerialName("translation_text") val translationText: Map<String, String>)
