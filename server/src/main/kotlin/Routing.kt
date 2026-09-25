package no.esotericgames.quotes.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import no.esotericgames.quotes.server.admin.ApproveImportedQuoteRequest
import no.esotericgames.quotes.server.admin.BulkDeleteImportedQuotesRequest
import no.esotericgames.quotes.server.admin.BulkUpdateImportedQuoteStatusRequest
import no.esotericgames.quotes.server.admin.ExtractQuoteExcerptsRequest
import no.esotericgames.quotes.server.admin.ImportedQuoteAdminService
import no.esotericgames.quotes.server.admin.ImportedQuoteFilter
import no.esotericgames.quotes.server.admin.NewSourceRequest
import no.esotericgames.quotes.server.admin.QuoteAdminService
import no.esotericgames.quotes.server.admin.QuoteFilter
import no.esotericgames.quotes.server.admin.UpdateImportedQuoteStatusRequest
import no.esotericgames.quotes.server.extraction.QuoteExtractionService
import no.esotericgames.quotes.server.sources.bhagavadgita.BhagavadGitaImportService
import no.esotericgames.quotes.server.sources.bible.BibleImportService
import no.esotericgames.quotes.server.sources.dhammapada.DhammapadaImportService
import no.esotericgames.quotes.server.sources.quran.QuranImportService
import no.esotericgames.quotes.server.sources.taote.TaoTeChingImportService
import no.esotericgames.quotes.server.sources.wikiquote.WikiquoteImportService
import no.esotericgames.quotes.server.wikidata.AuthorEnrichmentService

fun Application.configureRouting(
    publicQuoteService: PublicQuoteService,
    wikiquoteImportService: WikiquoteImportService,
    importedQuoteAdminService: ImportedQuoteAdminService,
    quoteAdminService: QuoteAdminService,
    taoTeChingImportService: TaoTeChingImportService,
    bhagavadGitaImportService: BhagavadGitaImportService,
    dhammapadaImportService: DhammapadaImportService,
    authorEnrichmentService: AuthorEnrichmentService,
    bibleImportService: BibleImportService,
    quranImportService: QuranImportService,
    quoteExtractionService: QuoteExtractionService,
) {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        get("/api/quotes") {
            call.respond(sampleQuotes)
        }
        get("/api/quotes/random") {
            val count = call.request.queryParameters["count"]?.toIntOrNull() ?: PublicQuoteService.DEFAULT_COUNT
            call.respond(publicQuoteService.randomQuotes(count))
        }
        post("/admin/import/wikiquote") {
            val request = call.receive<WikiquoteImportRequest>()
            call.respond(wikiquoteImportService.import(request))
        }
        get("/admin/import/wikiquote/authors") {
            val query = call.request.queryParameters["q"].orEmpty()
            call.respond(WikiquoteAuthorSearchResponse(wikiquoteImportService.searchAuthors(query)))
        }
        post("/admin/import/tao-te-ching") {
            call.respond(taoTeChingImportService.import())
        }
        post("/admin/import/bhagavad-gita") {
            call.respond(bhagavadGitaImportService.import())
        }
        post("/admin/import/dhammapada") {
            call.respond(dhammapadaImportService.import())
        }
        post("/admin/import/bible") {
            call.respond(bibleImportService.import())
        }
        post("/admin/import/quran") {
            call.respond(quranImportService.import())
        }
        get("/admin/imported-quotes") {
            val params = call.request.queryParameters
            val filter = ImportedQuoteFilter(
                statuses = params.getAll("status")?.toSet(),
                provider = params["provider"],
                sourceConfidence = params["sourceConfidence"],
                search = params["search"],
                page = params["page"]?.toIntOrNull() ?: 1,
                pageSize = params["pageSize"]?.toIntOrNull() ?: 200,
            )
            call.respond(importedQuoteAdminService.listImportedQuotes(filter))
        }
        patch("/admin/imported-quotes/{id}/status") {
            val id = call.parameters.getOrFail("id").toInt()
            val request = call.receive<UpdateImportedQuoteStatusRequest>()
            call.respond(importedQuoteAdminService.updateStatus(id, request.status, request.reviewedBy, request.reviewNote))
        }
        post("/admin/imported-quotes/bulk/status") {
            val request = call.receive<BulkUpdateImportedQuoteStatusRequest>()
            call.respond(
                importedQuoteAdminService.bulkUpdateStatus(request.ids, request.status, request.reviewedBy, request.reviewNote),
            )
        }
        post("/admin/imported-quotes/bulk/delete") {
            val request = call.receive<BulkDeleteImportedQuotesRequest>()
            call.respond(importedQuoteAdminService.bulkDelete(request.ids))
        }
        post("/admin/imported-quotes/{id}/approve") {
            val id = call.parameters.getOrFail("id").toInt()
            val request = call.receive<ApproveImportedQuoteRequest>()
            call.respond(importedQuoteAdminService.approve(id, request))
        }
        delete("/admin/imported-quotes/{id}") {
            val id = call.parameters.getOrFail("id").toInt()
            importedQuoteAdminService.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/admin/quotes") {
            val params = call.request.queryParameters
            val filter = QuoteFilter(
                authorId = params["authorId"]?.toIntOrNull(),
                verified = params["verified"]?.toBooleanStrictOrNull(),
                language = params["language"],
                search = params["search"],
                page = params["page"]?.toIntOrNull() ?: 1,
                pageSize = params["pageSize"]?.toIntOrNull() ?: 50,
            )
            call.respond(quoteAdminService.listQuotes(filter))
        }
        get("/admin/authors") {
            call.respond(importedQuoteAdminService.listAuthors())
        }
        post("/admin/authors/enrich") {
            call.respond(authorEnrichmentService.enrichAuthorsFromWikidata())
        }
        get("/admin/sources") {
            call.respond(importedQuoteAdminService.listSources())
        }
        post("/admin/sources") {
            val request = call.receive<NewSourceRequest>()
            call.respond(HttpStatusCode.Created, importedQuoteAdminService.createSource(request))
        }
        get("/admin/source-types") {
            call.respond(importedQuoteAdminService.listSourceTypes())
        }
        post("/admin/quotes/extract-excerpts") {
            val request = call.receive<ExtractQuoteExcerptsRequest>()
            call.respond(quoteExtractionService.extractForQuotes(request.quoteIds))
        }
    }
}
