package no.esotericgames.quotes.server

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        get("/api/quotes") {
            call.respond(sampleQuotes)
        }
    }
}
