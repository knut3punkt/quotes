package no.esotericgames.quotes.server.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.esotericgames.quotes.server.db.QuoteExcerpts
import no.esotericgames.quotes.server.db.QuoteTagAssignments
import no.esotericgames.quotes.server.db.Quotes
import no.esotericgames.quotes.server.db.Tags
import no.esotericgames.quotes.server.tagging.MAX_RELEVANCE
import no.esotericgames.quotes.server.tagging.MIN_RELEVANCE
import no.esotericgames.quotes.server.tagging.ORIGIN_ADMIN
import no.esotericgames.quotes.server.tagging.TagBasis
import no.esotericgames.quotes.server.tagging.TagBreadth
import no.esotericgames.quotes.server.tagging.TagFacet
import no.esotericgames.quotes.server.tagging.TagNormalization
import no.esotericgames.quotes.server.tagging.TagVocabulary
import no.esotericgames.quotes.server.tagging.quoteTagSubjectMatches
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update

private const val MAX_TAG_LIST_LIMIT = 2000

/**
 * Admin edits to the tag vocabulary and to individual quotes' tags (docs/features/quote-tagging.md).
 *
 * Removing a tag from a quote never deletes the row: it marks it rejected, so a later LLM run on that
 * quote won't add the tag back. Adding a tag the admin previously removed simply un-rejects it.
 */
class TagAdminService {

    suspend fun listTags(facet: String?, search: String?, limit: Int): List<TagSummaryResponse> =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                val parsedFacet = facet?.let { parseFacet(it) }
                val tags = TagVocabulary.listCanonical(parsedFacet, search, limit.coerceIn(1, MAX_TAG_LIST_LIMIT))
                val aliasesByTagId = if (tags.isEmpty()) {
                    emptyMap()
                } else {
                    Tags.selectAll().where { Tags.mergedIntoId inList tags.map { it.id } }
                        .groupBy({ it[Tags.mergedIntoId]!! }, { it[Tags.name] })
                }
                tags.map {
                    TagSummaryResponse(
                        id = it.id,
                        facet = it.facet.dbValue,
                        name = it.name,
                        breadth = it.breadth?.dbValue,
                        usageCount = it.usageCount,
                        aliases = aliasesByTagId[it.id].orEmpty().sorted(),
                    )
                }
            }
        }

    suspend fun updateTag(tagId: Int, request: UpdateTagRequest): TagSummaryResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            val tag = Tags.selectAll().where { Tags.id eq tagId }.firstOrNull()
                ?: throw NoSuchElementException("tag $tagId not found")
            require(tag[Tags.mergedIntoId] == null) { "tag $tagId is an alias; edit the tag it was merged into" }
            val facet = parseFacet(tag[Tags.facet])

            val newName = request.name?.let {
                TagNormalization.normalizeKeepingDisplay(it, facet) ?: throw IllegalArgumentException("tag name is empty")
            }
            if (newName != null) {
                val clash = Tags.selectAll().where {
                    (Tags.facet eq facet.dbValue) and (Tags.normalizedName eq newName.normalizedName) and (Tags.id neq tagId)
                }.firstOrNull()
                if (clash != null) {
                    throw IllegalStateException("a ${facet.dbValue} tag named '${clash[Tags.name]}' already exists; merge into it instead")
                }
            }
            val newBreadth = request.breadth?.let {
                require(facet == TagFacet.CONCEPT) { "only concept tags have a breadth" }
                parseBreadth(it)
            }

            Tags.update({ Tags.id eq tagId }) {
                newName?.let { name ->
                    it[Tags.name] = name.displayName
                    it[normalizedName] = name.normalizedName
                }
                newBreadth?.let { breadth -> it[Tags.breadth] = breadth.dbValue }
            }
            summaryOf(tagId)
        }
    }

    /**
     * Merges [tagId] into [request]'s target: every assignment moves to the target, and [tagId] becomes an
     * alias of it. Where a subject already has both tags, the target's row is kept, taking the stronger
     * relevance and basis. A rejection on either row wins, since the two were judged to be the same tag.
     */
    suspend fun mergeTag(tagId: Int, request: MergeTagRequest): TagSummaryResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            val source = Tags.selectAll().where { Tags.id eq tagId }.firstOrNull()
                ?: throw NoSuchElementException("tag $tagId not found")
            Tags.selectAll().where { Tags.id eq request.intoTagId }.firstOrNull()
                ?: throw NoSuchElementException("tag ${request.intoTagId} not found")
            val targetId = TagVocabulary.canonicalId(request.intoTagId)
            val target = Tags.selectAll().where { Tags.id eq targetId }.single()
            require(targetId != tagId) { "cannot merge a tag into itself" }
            require(source[Tags.facet] == target[Tags.facet]) { "can only merge tags of the same facet" }

            val sourceRows = QuoteTagAssignments.selectAll().where { QuoteTagAssignments.tagId eq tagId }.toList()
            sourceRows.forEach { row -> moveAssignment(row, targetId) }

            Tags.update({ Tags.mergedIntoId eq tagId }) { it[mergedIntoId] = targetId }
            Tags.update({ Tags.id eq tagId }) { it[mergedIntoId] = targetId }
            summaryOf(targetId)
        }
    }

    suspend fun addTagToQuote(quoteId: Int, request: AddQuoteTagRequest): QuoteTagResponse = withContext(Dispatchers.IO) {
        suspendTransaction {
            Quotes.selectAll().where { Quotes.id eq quoteId }.firstOrNull()
                ?: throw NoSuchElementException("quote $quoteId not found")
            request.excerptId?.let { excerptId ->
                QuoteExcerpts.selectAll()
                    .where { (QuoteExcerpts.id eq excerptId) and (QuoteExcerpts.quoteId eq quoteId) }
                    .firstOrNull()
                    ?: throw NoSuchElementException("excerpt $excerptId not found on quote $quoteId")
            }
            val facet = parseFacet(request.facet)
            val name = TagNormalization.normalize(request.name, facet) ?: throw IllegalArgumentException("tag name is empty")
            val breadth = if (facet == TagFacet.CONCEPT) request.breadth?.let(::parseBreadth) ?: TagBreadth.SPECIFIC else null
            require(request.relevance in MIN_RELEVANCE..MAX_RELEVANCE) { "relevance must be $MIN_RELEVANCE-$MAX_RELEVANCE" }

            val tagId = TagVocabulary.resolveOrCreate(facet, name, breadth, createdBy = ORIGIN_ADMIN)
            val existing = QuoteTagAssignments.selectAll()
                .where { quoteTagSubjectMatches(quoteId, request.excerptId) and (QuoteTagAssignments.tagId eq tagId) }
                .firstOrNull()

            val assignmentId = if (existing != null) {
                QuoteTagAssignments.update({ QuoteTagAssignments.id eq existing[QuoteTagAssignments.id] }) {
                    it[rejected] = false
                    it[origin] = ORIGIN_ADMIN
                    it[relevance] = request.relevance
                }
                existing[QuoteTagAssignments.id]
            } else {
                QuoteTagAssignments.insert {
                    it[QuoteTagAssignments.quoteId] = quoteId
                    it[excerptId] = request.excerptId
                    it[QuoteTagAssignments.tagId] = tagId
                    it[origin] = ORIGIN_ADMIN
                    it[relevance] = request.relevance
                    it[basis] = TagBasis.TEXT.dbValue
                }[QuoteTagAssignments.id]
            }
            selectActiveQuoteTags(listOf(quoteId)).single { it.assignmentId == assignmentId }
        }
    }

    suspend fun rejectAssignment(assignmentId: Int) = withContext(Dispatchers.IO) {
        suspendTransaction {
            val updated = QuoteTagAssignments.update({ QuoteTagAssignments.id eq assignmentId }) { it[rejected] = true }
            if (updated == 0) throw NoSuchElementException("tag assignment $assignmentId not found")
        }
    }

    private fun moveAssignment(row: ResultRow, targetId: Int) {
        val quoteId = row[QuoteTagAssignments.quoteId]
        val excerptId = row[QuoteTagAssignments.excerptId]
        val targetRow = QuoteTagAssignments.selectAll()
            .where { quoteTagSubjectMatches(quoteId, excerptId) and (QuoteTagAssignments.tagId eq targetId) }
            .firstOrNull()

        if (targetRow == null) {
            QuoteTagAssignments.update({ QuoteTagAssignments.id eq row[QuoteTagAssignments.id] }) { it[tagId] = targetId }
            return
        }
        val textBased = row[QuoteTagAssignments.basis] == TagBasis.TEXT.dbValue ||
            targetRow[QuoteTagAssignments.basis] == TagBasis.TEXT.dbValue
        QuoteTagAssignments.update({ QuoteTagAssignments.id eq targetRow[QuoteTagAssignments.id] }) {
            it[relevance] = maxOf(row[QuoteTagAssignments.relevance], targetRow[QuoteTagAssignments.relevance])
            it[basis] = if (textBased) TagBasis.TEXT.dbValue else TagBasis.INTERPRETATION.dbValue
            it[rejected] = row[QuoteTagAssignments.rejected] || targetRow[QuoteTagAssignments.rejected]
            if (row[QuoteTagAssignments.origin] == ORIGIN_ADMIN) it[origin] = ORIGIN_ADMIN
        }
        QuoteTagAssignments.deleteWhere { QuoteTagAssignments.id eq row[QuoteTagAssignments.id] }
    }

    private fun summaryOf(tagId: Int): TagSummaryResponse {
        val tag = Tags.selectAll().where { Tags.id eq tagId }.single()
        val usageCount = QuoteTagAssignments.selectAll()
            .where { (QuoteTagAssignments.tagId eq tagId) and (QuoteTagAssignments.rejected eq false) }
            .count()
        val aliases = Tags.selectAll().where { Tags.mergedIntoId eq tagId }.map { it[Tags.name] }.sorted()
        return TagSummaryResponse(
            id = tagId,
            facet = tag[Tags.facet],
            name = tag[Tags.name],
            breadth = tag[Tags.breadth],
            usageCount = usageCount,
            aliases = aliases,
        )
    }
}

private fun parseFacet(value: String): TagFacet =
    TagFacet.fromDbValue(value) ?: throw IllegalArgumentException("unknown tag facet '$value'")

private fun parseBreadth(value: String): TagBreadth =
    TagBreadth.fromDbValue(value) ?: throw IllegalArgumentException("unknown breadth '$value'")

/** Active (not rejected) tags on the given quotes and their excerpts. Must run inside a transaction. */
fun selectActiveQuoteTags(quoteIds: Collection<Int>): List<QuoteTagResponse> {
    if (quoteIds.isEmpty()) return emptyList()
    return QuoteTagAssignments.innerJoin(Tags)
        .selectAll()
        .where { (QuoteTagAssignments.quoteId inList quoteIds) and (QuoteTagAssignments.rejected eq false) }
        .map {
            QuoteTagResponse(
                assignmentId = it[QuoteTagAssignments.id],
                quoteId = it[QuoteTagAssignments.quoteId],
                excerptId = it[QuoteTagAssignments.excerptId],
                tagId = it[Tags.id],
                facet = it[Tags.facet],
                name = it[Tags.name],
                breadth = it[Tags.breadth],
                relevance = it[QuoteTagAssignments.relevance],
                basis = it[QuoteTagAssignments.basis],
                origin = it[QuoteTagAssignments.origin],
            )
        }
        .sortedWith(compareByDescending<QuoteTagResponse> { it.relevance }.thenBy { it.name })
}
