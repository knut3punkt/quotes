package no.esotericgames.quotes.server

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.esotericgames.quotes.server.wikiquote.WikiquoteImportService

fun Application.configureRouting(wikiquoteImportService: WikiquoteImportService) {
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
    }
}
