package no.esotericgames.quotes.server.bhagavadgita

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

private const val BASE_URL = "https://vedicscriptures.github.io"
private const val USER_AGENT = "TVQuotes-Importer/1.0 (contact: knut3punkt@gmail.com)"
private const val REQUEST_INTERVAL_MILLIS = 150L

/** Thin wrapper around the free, unauthenticated vedicscriptures.github.io Bhagavad Gita API. */
class BhagavadGitaClient(
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(UserAgent) {
            agent = USER_AGENT
        }
    },
) {
    suspend fun fetchChapter(chapter: Int): GitaChapter {
        delay(REQUEST_INTERVAL_MILLIS)
        return httpClient.get("$BASE_URL/chapter/$chapter/").body()
    }

    suspend fun fetchVerse(chapter: Int, verse: Int): GitaVerse {
        delay(REQUEST_INTERVAL_MILLIS)
        return httpClient.get("$BASE_URL/slok/$chapter/$verse/").body()
    }
}

@Serializable
data class GitaChapter(
    @SerialName("chapter_number") val chapterNumber: Int,
    @SerialName("verses_count") val versesCount: Int,
    val translation: String,
)

@Serializable
data class GitaVerse(
    @SerialName("_id") val id: String,
    val chapter: Int,
    val verse: Int,
    val slok: String,
    val transliteration: String,
    // Only Shri Purohit Swami's translation (d. 1941) is used as quote text — his work is credibly
    // public domain in the vast majority of jurisdictions (life+70 expired in 2011). The other
    // commentator fields (Sivananda, Tejomayananda, Prabhupada, ...) are all by 20th-century authors
    // who died well within the last 70 years and are deliberately never read by this importer.
    val purohit: GitaCommentary? = null,
)

@Serializable
data class GitaCommentary(val author: String, val et: String? = null)
