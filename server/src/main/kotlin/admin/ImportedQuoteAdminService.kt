package no.esotericgames.quotes.server.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.Authors
import no.esotericgames.quotes.server.db.ImportedQuotes
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Sources
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update

private val MANUAL_STATUSES = setOf("pending", "rejected", "duplicate")

class ImportedQuoteAdminService {

    suspend fun listImportedQuotes(): List<ImportedQuoteResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            ImportedQuotes.selectAll()
                .orderBy(ImportedQuotes.importedAt to SortOrder.DESC)
                .map { it.toImportedQuoteResponse() }
        }
    }

    suspend fun updateStatus(id: Int, status: String): ImportedQuoteResponse {
        require(status in MANUAL_STATUSES) { "status must be one of $MANUAL_STATUSES" }
        return withContext(Dispatchers.IO) {
            suspendTransaction {
                val updatedRows = ImportedQuotes.update({ ImportedQuotes.id eq id }) {
                    it[processingStatus] = status
                }
                if (updatedRows == 0) throw NoSuchElementException("imported quote $id not found")
                ImportedQuotes.selectAll().where { ImportedQuotes.id eq id }.first().toImportedQuoteResponse()
            }
        }
    }

    suspend fun approve(id: Int, request: ApproveImportedQuoteRequest): QuoteResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            val importedRow = ImportedQuotes.selectAll().where { ImportedQuotes.id eq id }.firstOrNull()
                ?: throw NoSuchElementException("imported quote $id not found")

            val authorId = request.authorId
                ?: request.newAuthorName?.let { name -> Authors.insert { it[Authors.name] = name }[Authors.id] }
                ?: throw IllegalArgumentException("either authorId or newAuthorName is required")

            val quoteText = request.text ?: importedRow[ImportedQuotes.rawText]
            val insertedQuoteId = Quotes.insert {
                it[text] = quoteText
                it[Quotes.authorId] = authorId
                it[sourceId] = request.sourceId
                it[sourceDetail] = request.sourceDetail
                it[verified] = request.verified
            }[Quotes.id]

            ImportedQuotes.update({ ImportedQuotes.id eq id }) {
                it[quoteId] = insertedQuoteId
                it[processingStatus] = "approved"
            }

            QuoteResponse(
                id = insertedQuoteId,
                text = quoteText,
                authorId = authorId,
                sourceId = request.sourceId,
                sourceDetail = request.sourceDetail,
                verified = request.verified,
            )
        }
    }

    suspend fun delete(id: Int): Unit = withContext(Dispatchers.IO) {
        suspendTransaction {
            val deletedRows = ImportedQuotes.deleteWhere { ImportedQuotes.id eq id }
            if (deletedRows == 0) throw NoSuchElementException("imported quote $id not found")
        }
    }

    suspend fun listAuthors(): List<AuthorResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            Authors.selectAll().orderBy(Authors.name to SortOrder.ASC).map {
                AuthorResponse(id = it[Authors.id], name = it[Authors.name])
            }
        }
    }

    suspend fun listSources(): List<SourceResponse> = withContext(Dispatchers.IO) {
        suspendTransaction {
            Sources.selectAll().orderBy(Sources.title to SortOrder.ASC).map {
                SourceResponse(id = it[Sources.id], title = it[Sources.title])
            }
        }
    }
}

private fun ResultRow.toImportedQuoteResponse() = ImportedQuoteResponse(
    id = this[ImportedQuotes.id],
    provider = this[ImportedQuotes.provider],
    providerQuoteId = this[ImportedQuotes.providerQuoteId],
    rawText = this[ImportedQuotes.rawText],
    rawAuthor = this[ImportedQuotes.rawAuthor],
    rawPayload = this[ImportedQuotes.rawPayload],
    importedAt = this[ImportedQuotes.importedAt].toString(),
    processingStatus = this[ImportedQuotes.processingStatus],
    quoteId = this[ImportedQuotes.quoteId],
    sourceConfidence = this[ImportedQuotes.sourceConfidence],
)
