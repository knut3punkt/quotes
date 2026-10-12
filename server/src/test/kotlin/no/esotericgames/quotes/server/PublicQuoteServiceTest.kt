package no.esotericgames.quotes.server

import io.ktor.client.request.get
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.Quotes
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicQuoteServiceTest {

    // Needs a real database connection, so it loads application.conf plus the local dev DB credentials,
    // like ApplicationTest's DB-backed test.
    @Test
    fun `a quote too long to show in full is only ever shown through an excerpt`() = testApplication {
        loadDotEnvIntoSystemProperties()
        environment { config = ApplicationConfig("application.conf") }
        client.get("/health") // starts the application, and with it the database connection

        val longText = (1..80).joinToString(" ") { "word$it" } + "."
        val longQuoteId = withContext(Dispatchers.IO) {
            suspendTransaction {
                Quotes.insert {
                    it[text] = longText
                    it[normalizedText] = longText
                }[Quotes.id]
            }
        }

        try {
            val storedWordCount = withContext(Dispatchers.IO) {
                suspendTransaction { Quotes.selectAll().where { Quotes.id eq longQuoteId }.single()[Quotes.wordCount] }
            }
            assertEquals(80, storedWordCount)

            val service = PublicQuoteService(PublicQuoteConfig(maxFullQuoteWords = 55))
            repeat(5) {
                service.randomQuotes(PublicQuoteService.MAX_COUNT).forEach { quote ->
                    if (quote.excerptId == null) {
                        val words = quote.text.trim().split(Regex("""\s+""")).size
                        assertTrue(words <= 55, "quote ${quote.id} shown in full with $words words")
                    }
                }
            }
        } finally {
            withContext(Dispatchers.IO) {
                suspendTransaction { Quotes.deleteWhere { Quotes.id eq longQuoteId } }
            }
        }
    }
}
