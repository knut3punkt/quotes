package no.esotericgames.quotes.server

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import no.esotericgames.quotes.server.admin.ApproveImportedQuoteRequest
import no.esotericgames.quotes.server.admin.ImportedQuoteAdminService
import no.esotericgames.quotes.server.admin.UpdateImportedQuoteStatusRequest
import no.esotericgames.quotes.server.wikiquote.WikiquoteImportService

fun Application.configureRouting(
    wikiquoteImportService: WikiquoteImportService,
    importedQuoteAdminService: ImportedQuoteAdminService,
) {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        get("/api/quotes") {
            call.respond(sampleQuotes)
        }
        post("/admin/import/wikiquote") {
            val request = call.receive<WikiquoteImportRequest>()
            call.respond(wikiquoteImportService.import(request))
        }
        get("/admin/imported-quotes") {
            call.respond(importedQuoteAdminService.listImportedQuotes())
        }
        patch("/admin/imported-quotes/{id}/status") {
            val id = call.parameters.getOrFail("id").toInt()
            val request = call.receive<UpdateImportedQuoteStatusRequest>()
            call.respond(importedQuoteAdminService.updateStatus(id, request.status))
        }
        post("/admin/imported-quotes/{id}/approve") {
            val id = call.parameters.getOrFail("id").toInt()
            val request = call.receive<ApproveImportedQuoteRequest>()
            call.respond(importedQuoteAdminService.approve(id, request))
        }
        get("/admin/authors") {
            call.respond(importedQuoteAdminService.listAuthors())
        }
        get("/admin/sources") {
            call.respond(importedQuoteAdminService.listSources())
        }
    }
}
