package no.esotericgames.quotes.server.httpclient

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Sent on every outbound call this server makes to a free/public third-party API (Wikiquote,
 * Wikidata, bible-api.com, ...), since several of those providers' usage policies ask for a contact
 * address in the user agent.
 */
const val EXTERNAL_API_USER_AGENT = "TVQuotes-Importer/1.0 (contact: knut3punkt@gmail.com)"

/**
 * An [HttpClient] configured the same way for every free/public scripture and metadata API this
 * server talks to: lenient JSON parsing (these APIs routinely carry fields this codebase doesn't
 * model) and [EXTERNAL_API_USER_AGENT]. Each caller still owns its own base URL, rate-limit pacing,
 * and response types.
 */
fun externalApiHttpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(UserAgent) {
        agent = EXTERNAL_API_USER_AGENT
    }
}

/**
 * Issues a GET built by [block] and, if the response is 429, waits out the `Retry-After` header (or
 * [defaultRetryAfterSeconds] if the response didn't send one) and retries exactly once. Shared by the
 * two providers (Wikiquote, bible-api.com) whose documented rate limits this server can realistically
 * hit within a single import run.
 */
suspend fun HttpClient.getWithRetryAfter(
    urlString: String,
    defaultRetryAfterSeconds: Long,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    val response = get(urlString, block)
    if (response.status != HttpStatusCode.TooManyRequests) return response
    val retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull() ?: defaultRetryAfterSeconds
    delay(retryAfterSeconds * 1000)
    return get(urlString, block)
}
