package no.esotericgames.quotes.server.admin

import kotlinx.serialization.Serializable

@Serializable
data class GenerateQuoteTagsRequest(val quoteIds: List<Int>)

/** One active (not rejected) tag on a quote, or on one of its excerpts when [excerptId] is set. */
@Serializable
data class QuoteTagResponse(
    val assignmentId: Int,
    val quoteId: Int,
    val excerptId: Int? = null,
    val tagId: Int,
    val facet: String, // "concept" | "mood" | "motif"
    val name: String,
    val breadth: String? = null, // "broad" | "specific", concepts only
    val relevance: Int, // 1-3
    val basis: String, // "text" | "interpretation"
    val origin: String, // "llm" | "admin"
)

@Serializable
data class QuoteTaggingResult(
    val quoteId: Int,
    val outcome: String, // "tagged" | "noTagsFound" | "failed" | "notFound"
    // Every active tag on the quote after the run, including admin-added ones, so the client can replace its copy.
    val tags: List<QuoteTagResponse>,
)

@Serializable
data class GenerateQuoteTagsResponse(val results: List<QuoteTaggingResult>)

@Serializable
data class TagSummaryResponse(
    val id: Int,
    val facet: String,
    val name: String,
    val breadth: String? = null,
    val usageCount: Long,
    val aliases: List<String> = emptyList(),
)

@Serializable
data class UpdateTagRequest(val name: String? = null, val breadth: String? = null)

@Serializable
data class MergeTagRequest(val intoTagId: Int)

@Serializable
data class AddQuoteTagRequest(
    val facet: String,
    val name: String,
    val excerptId: Int? = null,
    val breadth: String? = null,
    val relevance: Int = 2,
)
