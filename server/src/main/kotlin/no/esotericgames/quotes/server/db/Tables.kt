package no.esotericgames.quotes.server.db

import org.jetbrains.exposed.v1.core.Table

object Authors : Table("authors") {
    val id = integer("id").autoIncrement()
    val name = text("name")
    val birthYear = integer("birth_year").nullable()
    val deathYear = integer("death_year").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Sources : Table("sources") {
    val id = integer("id").autoIncrement()
    val title = text("title")
    val type = text("type")
    val year = integer("year").nullable()
    val url = text("url").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Quotes : Table("quotes") {
    val id = integer("id").autoIncrement()
    val text = text("text")
    val authorId = integer("author_id").references(Authors.id)
    val sourceId = integer("source_id").references(Sources.id).nullable()
    val sourceDetail = text("source_detail").nullable()
    val verified = bool("verified").default(false)

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
