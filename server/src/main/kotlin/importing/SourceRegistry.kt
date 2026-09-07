package no.esotericgames.quotes.server.importing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.SourceTypes
import no.esotericgames.quotes.server.db.Sources
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

data class SourceDescriptor(
    val title: String,
    val typeCode: String,
    val year: Int? = null,
    val url: String? = null,
    val citationUnit: String? = null,
    val license: String? = null,
    val attributionText: String? = null,
)

/**
 * Finds an existing `sources` row matching [descriptor] (case-insensitive title + type), or creates
 * one. Shared by the admin API's `POST /admin/sources` / inline approve-time source creation and
 * every scripture importer, so a batch import (e.g. all 81 Tao Te Ching chapters) only ever creates
 * its source row once instead of racing duplicate inserts.
 */
suspend fun findOrCreateSource(descriptor: SourceDescriptor): Int = withContext(Dispatchers.IO) {
    suspendTransaction {
        val existing = Sources.selectAll()
            .where { (Sources.title.lowerCase() eq descriptor.title.lowercase()) and (Sources.typeCode eq descriptor.typeCode) }
            .firstOrNull()
        if (existing != null) return@suspendTransaction existing[Sources.id]

        val validTypeCodes = SourceTypes.selectAll().map { it[SourceTypes.code] }.toSet()
        require(descriptor.typeCode in validTypeCodes) { "typeCode must be one of $validTypeCodes" }
        Sources.insert {
            it[title] = descriptor.title
            it[typeCode] = descriptor.typeCode
            it[year] = descriptor.year
            it[url] = descriptor.url
            it[citationUnit] = descriptor.citationUnit
            it[license] = descriptor.license
            it[attributionText] = descriptor.attributionText
        }[Sources.id]
    }
}
