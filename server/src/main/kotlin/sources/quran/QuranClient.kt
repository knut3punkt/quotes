package no.esotericgames.quotes.server.sources.quran

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.httpclient.externalApiHttpClient

private const val BASE_URL = "https://api.alquran.cloud/v1"
private const val REQUEST_INTERVAL_MILLIS = 300L

/** Thin wrapper around alquran.cloud's per-ayah lookup, addressed as "surah:ayah". */
class QuranClient(
    private val httpClient: HttpClient = externalApiHttpClient(),
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
