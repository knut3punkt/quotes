package no.esotericgames.quotes.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import no.esotericgames.quotes.server.db.configureDatabase
import no.esotericgames.quotes.server.wikiquote.WikiquoteClient
import no.esotericgames.quotes.server.wikiquote.WikiquoteImportService

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    configureDatabase()
    configureRouting(WikiquoteImportService(WikiquoteClient()))
}
