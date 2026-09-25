package no.esotericgames.quotes.server.wikiquote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import no.esotericgames.quotes.server.httpclient.externalApiHttpClient
import no.esotericgames.quotes.server.httpclient.getWithRetryAfter

private const val WIKIQUOTE_API_BASE_URL = "https://en.wikiquote.org/w/api.php"
private const val REQUEST_INTERVAL_MILLIS = 250L
private const val DEFAULT_RETRY_AFTER_SECONDS = 2L

/**
 * Thin wrapper around the MediaWiki API on en.wikiquote.org. Every call is serial (never fired
 * concurrently by this class) and paced with a small delay per the Wikimedia API etiquette policy.
 */
class WikiquoteClient(
    private val httpClient: HttpClient = externalApiHttpClient(),
) {
    suspend fun resolveTitle(name: String): String? {
        val response = apiGet {
            parameter("action", "query")
            parameter("titles", name)
            parameter("redirects", "1")
            parameter("format", "json")
            parameter("formatversion", "2")
        }
        val page = response.body<WikiquoteQueryResponse>().query.pages.firstOrNull() ?: return null
        return if (page.missing == true) null else page.title
    }

    suspend fun listTopLevelSections(title: String): List<WikiquoteSection> {
        val response = apiGet {
            parameter("action", "parse")
            parameter("page", title)
            parameter("prop", "tocdata")
            parameter("redirects", "1")
            parameter("format", "json")
            parameter("formatversion", "2")
        }
        return response.body<WikiquoteParseTocResponse>().parse.tocdata.sections.filter { it.tocLevel == 1 }
    }

    suspend fun fetchSectionHtml(title: String, sectionIndex: String): WikiquoteSectionContent {
        val response = apiGet {
            parameter("action", "parse")
            parameter("page", title)
            parameter("section", sectionIndex)
            parameter("prop", "text|revid")
            parameter("redirects", "1")
            parameter("format", "json")
            parameter("formatversion", "2")
        }
        val parse = response.body<WikiquoteParseTextResponse>().parse
        return WikiquoteSectionContent(revisionId = parse.revid, html = parse.text)
    }

    suspend fun searchTitles(query: String, limit: Int = 8): List<String> {
        val response = apiGet {
            parameter("action", "query")
            parameter("list", "prefixsearch")
            parameter("pssearch", query)
            parameter("psnamespace", "0")
            parameter("pslimit", limit.toString())
            parameter("format", "json")
            parameter("formatversion", "2")
        }
        return response.body<WikiquotePrefixSearchResponse>().query.prefixsearch.map { it.title }
    }

    private suspend fun apiGet(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        delay(REQUEST_INTERVAL_MILLIS)
        return httpClient.getWithRetryAfter(WIKIQUOTE_API_BASE_URL, DEFAULT_RETRY_AFTER_SECONDS, block)
    }
}

data class WikiquoteSectionContent(val revisionId: Long, val html: String)

@Serializable
data class WikiquoteSection(val tocLevel: Int, val line: String, val index: String)

@Serializable
private data class WikiquoteQueryResponse(val query: WikiquoteQuery)

@Serializable
private data class WikiquoteQuery(val pages: List<WikiquotePage> = emptyList())

@Serializable
private data class WikiquotePage(val title: String, val missing: Boolean? = null)

@Serializable
private data class WikiquoteParseTocResponse(val parse: WikiquoteParseToc)

@Serializable
private data class WikiquoteParseToc(val tocdata: WikiquoteTocData)

@Serializable
private data class WikiquoteTocData(val sections: List<WikiquoteSection>)

@Serializable
private data class WikiquoteParseTextResponse(val parse: WikiquoteParseText)

@Serializable
private data class WikiquoteParseText(val revid: Long, val text: String)

@Serializable
private data class WikiquotePrefixSearchResponse(val query: WikiquotePrefixSearchQuery)

@Serializable
private data class WikiquotePrefixSearchQuery(val prefixsearch: List<WikiquotePrefixSearchResult> = emptyList())

@Serializable
private data class WikiquotePrefixSearchResult(val title: String)
