package no.esotericgames.quotes.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import no.esotericgames.quotes.server.admin.ApproveImportedQuoteRequest
import no.esotericgames.quotes.server.admin.BulkDeleteImportedQuotesRequest
import no.esotericgames.quotes.server.admin.BulkUnapproveQuotesRequest
import no.esotericgames.quotes.server.admin.BulkUpdateImportedQuoteStatusRequest
import no.esotericgames.quotes.server.admin.AddQuoteTagRequest
import no.esotericgames.quotes.server.admin.ExtractQuoteExcerptsRequest
import no.esotericgames.quotes.server.admin.GenerateQuoteInterpretationsRequest
import no.esotericgames.quotes.server.admin.GenerateQuoteTagsRequest
import no.esotericgames.quotes.server.admin.ImageGenerationJobState
import no.esotericgames.quotes.server.admin.ImportedQuoteAdminService
import no.esotericgames.quotes.server.admin.ImportedQuoteFilter
import no.esotericgames.quotes.server.admin.MergeTagRequest
import no.esotericgames.quotes.server.admin.NewSourceRequest
import no.esotericgames.quotes.server.admin.QuoteAdminService
import no.esotericgames.quotes.server.admin.QuoteFilter
import no.esotericgames.quotes.server.admin.TagAdminService
import no.esotericgames.quotes.server.admin.UpdateTagRequest
import no.esotericgames.quotes.server.admin.UpdateImportedQuoteStatusRequest
import no.esotericgames.quotes.server.extraction.QuoteExtractionService
import no.esotericgames.quotes.server.imagegen.TagImageFileService
import no.esotericgames.quotes.server.imagegen.TagImageGenerationJob
import no.esotericgames.quotes.server.interpretation.QuoteInterpretationService
import no.esotericgames.quotes.server.sources.bhagavadgita.BhagavadGitaImportService
import no.esotericgames.quotes.server.sources.bible.BibleImportService
import no.esotericgames.quotes.server.sources.dhammapada.DhammapadaImportService
import no.esotericgames.quotes.server.sources.quran.QuranImportService
import no.esotericgames.quotes.server.sources.taote.TaoTeChingImportService
import no.esotericgames.quotes.server.sources.wikiquote.WikiquoteImportService
import no.esotericgames.quotes.server.tagging.QuoteTaggingService
import no.esotericgames.quotes.server.wikidata.AuthorEnrichmentService

private const val IMAGE_GENERATION_PUSH_INTERVAL_MILLIS = 250L

// A tag image row is never rewritten in place, so a served file never changes under its URL.
private const val TAG_IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable"

// Matches the REST responses, which include default values such as an empty item list.
private val imageGenerationJson = Json { encodeDefaults = true }

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
    quoteInterpretationService: QuoteInterpretationService,
    quoteTaggingService: QuoteTaggingService,
    tagAdminService: TagAdminService,
    tagImageGenerationJob: TagImageGenerationJob,
    tagImageFileService: TagImageFileService,
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
        get("/api/quotes/{id}/visuals") {
            val id = call.parameters.getOrFail("id").toInt()
            call.respond(publicQuoteService.visualsFor(id))
        }
        get("/api/tag-images/{id}") {
            val id = call.parameters.getOrFail("id").toInt()
            val width = call.request.queryParameters["width"]?.toIntOrNull()
            val file = tagImageFileService.imageFile(id, width)
            val etag = "\"${file.etag}\""
            call.response.header(HttpHeaders.CacheControl, TAG_IMAGE_CACHE_CONTROL)
            call.response.header(HttpHeaders.ETag, etag)
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                call.respond(HttpStatusCode.NotModified)
            } else {
                call.respondFile(file.path.toFile())
            }
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
        post("/admin/quotes/bulk/unapprove") {
            val request = call.receive<BulkUnapproveQuotesRequest>()
            call.respond(quoteAdminService.bulkUnapprove(request.quoteIds))
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
        post("/admin/quotes/generate-interpretations") {
            val request = call.receive<GenerateQuoteInterpretationsRequest>()
            call.respond(quoteInterpretationService.generateForQuotes(request.quoteIds))
        }
        post("/admin/quotes/generate-tags") {
            val request = call.receive<GenerateQuoteTagsRequest>()
            call.respond(quoteTaggingService.generateForQuotes(request.quoteIds))
        }
        post("/admin/quotes/{id}/tags") {
            val id = call.parameters.getOrFail("id").toInt()
            val request = call.receive<AddQuoteTagRequest>()
            call.respond(HttpStatusCode.Created, tagAdminService.addTagToQuote(id, request))
        }
        post("/admin/tag-assignments/{id}/reject") {
            val id = call.parameters.getOrFail("id").toInt()
            tagAdminService.rejectAssignment(id)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/admin/tags") {
            val params = call.request.queryParameters
            call.respond(tagAdminService.listTags(params["facet"], params["search"], params["limit"]?.toIntOrNull() ?: 500))
        }
        patch("/admin/tags/{id}") {
            val id = call.parameters.getOrFail("id").toInt()
            call.respond(tagAdminService.updateTag(id, call.receive<UpdateTagRequest>()))
        }
        post("/admin/tags/{id}/merge") {
            val id = call.parameters.getOrFail("id").toInt()
            call.respond(tagAdminService.mergeTag(id, call.receive<MergeTagRequest>()))
        }
        post("/admin/image-generation/start") {
            call.respond(HttpStatusCode.Accepted, tagImageGenerationJob.start())
        }
        post("/admin/image-generation/cancel") {
            call.respond(tagImageGenerationJob.cancel())
        }
        get("/admin/image-generation/status") {
            call.respond(tagImageGenerationJob.state.value)
        }
        webSocket("/admin/image-generation/ws") {
            // StateFlow collection is conflated, so the delay throttles pushes to the latest state.
            val pushes = launch {
                tagImageGenerationJob.state.collect { state ->
                    send(Frame.Text(imageGenerationJson.encodeToString(ImageGenerationJobState.serializer(), state)))
                    delay(IMAGE_GENERATION_PUSH_INTERVAL_MILLIS)
                }
            }
            // The client sends nothing, but reading is what notices its Close frame: the handler then
            // returns, Ktor completes the close handshake, and the push coroutine is cancelled with it.
            try {
                for (frame in incoming) Unit
            } finally {
                pushes.cancel()
            }
        }
    }
}
