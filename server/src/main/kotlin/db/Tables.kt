package no.esotericgames.quotes.server.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object Authors : Table("authors") {
    val id = integer("id").autoIncrement()
    val name = text("name")
    val birthYear = integer("birth_year").nullable()
    val deathYear = integer("death_year").nullable()
    val normalizedName = text("normalized_name").uniqueIndex()
    val wikidataQid = text("wikidata_qid").nullable().uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object SourceTypes : Table("source_types") {
    val code = text("code")
    val description = text("description")

    override val primaryKey = PrimaryKey(code)
}

object Sources : Table("sources") {
    val id = integer("id").autoIncrement()
    val title = text("title")
    val typeCode = text("type_code").references(SourceTypes.code)
    val year = integer("year").nullable()
    val url = text("url").nullable()
    val citationUnit = text("citation_unit").nullable()
    val license = text("license").nullable()
    val attributionText = text("attribution_text").nullable()
    val translation = text("translation").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Quotes : Table("quotes") {
    val id = integer("id").autoIncrement()
    val text = text("text")
    val authorId = integer("author_id").references(Authors.id).nullable()
    val sourceId = integer("source_id").references(Sources.id).nullable()
    val sourceDetail = text("source_detail").nullable()
    val verified = bool("verified").default(false)
    val language = text("language").default("en")
    val normalizedText = text("normalized_text")

    override val primaryKey = PrimaryKey(id)
}

object Tags : Table("tags") {
    val id = integer("id").autoIncrement()
    val name = text("name").uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object QuoteTags : Table("quote_tags") {
    val quoteId = integer("quote_id").references(Quotes.id)
    val tagId = integer("tag_id").references(Tags.id)

    override val primaryKey = PrimaryKey(quoteId, tagId)
}

object ImportedQuotes : Table("imported_quotes") {
    val id = integer("id").autoIncrement()
    val provider = text("provider")
    val providerQuoteId = text("provider_quote_id")
    val rawText = text("raw_text")
    val rawAuthor = text("raw_author").nullable()
    val rawSourceLocation = text("raw_source_location").nullable()
    val rawSourceTitle = text("raw_source_title").nullable()
    val rawSourceYear = integer("raw_source_year").nullable()
    val sourceId = integer("source_id").references(Sources.id).nullable()
    val rawPayload = jsonb<JsonElement>("raw_payload", Json.Default)
    val importedAt = timestampWithTimeZone("imported_at").defaultExpression(CurrentTimestampWithTimeZone)
    val processingStatus = text("processing_status").default("pending")
    val quoteId = integer("quote_id").references(Quotes.id).nullable()
    val sourceConfidence = text("source_confidence").nullable()
    val reviewedBy = text("reviewed_by").nullable()
    val reviewedAt = timestampWithTimeZone("reviewed_at").nullable()
    val reviewNote = text("review_note").nullable()
    val duplicateOfId = integer("duplicate_of_id").references(id).nullable()
    val language = text("language").default("en")
    val normalizedText = text("normalized_text")
    val possibleDuplicateOfId = integer("possible_duplicate_of_id").references(id).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(provider, providerQuoteId)
    }
}
