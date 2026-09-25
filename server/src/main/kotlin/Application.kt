package no.esotericgames.quotes.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import no.esotericgames.quotes.server.admin.ImportedQuoteAdminService
import no.esotericgames.quotes.server.admin.QuoteAdminService
import no.esotericgames.quotes.server.db.configureDatabase
import no.esotericgames.quotes.server.extraction.QuoteExtractionService
import no.esotericgames.quotes.server.extraction.loadExtractionConfig
import no.esotericgames.quotes.server.extraction.llm.LlamaCppExcerptSelectionClient
import no.esotericgames.quotes.server.sources.bhagavadgita.BhagavadGitaClient
import no.esotericgames.quotes.server.sources.bhagavadgita.BhagavadGitaImportService
import no.esotericgames.quotes.server.sources.bible.BibleClient
import no.esotericgames.quotes.server.sources.bible.BibleImportService
import no.esotericgames.quotes.server.sources.dhammapada.DhammapadaClient
import no.esotericgames.quotes.server.sources.dhammapada.DhammapadaImportService
import no.esotericgames.quotes.server.sources.quran.QuranClient
import no.esotericgames.quotes.server.sources.quran.QuranImportService
import no.esotericgames.quotes.server.sources.taote.TaoTeChingImportService
import no.esotericgames.quotes.server.sources.wikiquote.WikiquoteClient
import no.esotericgames.quotes.server.sources.wikiquote.WikiquoteImportService
import no.esotericgames.quotes.server.wikidata.AuthorEnrichmentService
import no.esotericgames.quotes.server.wikidata.WikidataClient

fun main(args: Array<String>) {
    loadDotEnvIntoSystemProperties()
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
    }
    install(StatusPages) {
        exception<NoSuchElementException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to cause.message))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, mapOf("error" to cause.message))
        }
    }
    configureDatabase()
    val extractionConfig = loadExtractionConfig(environment.config)
    configureRouting(
        publicQuoteService = PublicQuoteService(),
        wikiquoteImportService = WikiquoteImportService(WikiquoteClient()),
        importedQuoteAdminService = ImportedQuoteAdminService(),
        quoteAdminService = QuoteAdminService(),
        taoTeChingImportService = TaoTeChingImportService(),
        bhagavadGitaImportService = BhagavadGitaImportService(BhagavadGitaClient()),
        dhammapadaImportService = DhammapadaImportService(DhammapadaClient()),
        authorEnrichmentService = AuthorEnrichmentService(WikidataClient()),
        bibleImportService = BibleImportService(BibleClient()),
        quranImportService = QuranImportService(QuranClient()),
        quoteExtractionService = QuoteExtractionService(LlamaCppExcerptSelectionClient(extractionConfig.llm), extractionConfig),
    )
}
