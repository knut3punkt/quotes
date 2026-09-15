package no.esotericgames.quotes.server.quran

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://api.alquran.cloud/v1"
private const val USER_AGENT = "TVQuotes-Importer/1.0 (contact: knut3punkt@gmail.com)"
private const val REQUEST_INTERVAL_MILLIS = 300L

/** Thin wrapper around alquran.cloud's per-ayah lookup, addressed as "surah:ayah". */
class QuranClient(
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(UserAgent) {
            agent = USER_AGENT
        }
    },
) {
    suspend fun fetchAyah(surah: Int, ayah: Int, edition: String): QuranAyah {
        delay(REQUEST_INTERVAL_MILLIS)
        return httpClient.get("$BASE_URL/ayah/$surah:$ayah/$edition").body<QuranAyahResponse>().data
    }
}

@Serializable
data class QuranAyahResponse(val data: QuranAyah)

@Serializable
data class QuranAyah(val number: Int, val text: String, val surah: QuranSurah, val numberInSurah: Int)

@Serializable
data class QuranSurah(val number: Int, val englishName: String, val englishNameTranslation: String)
