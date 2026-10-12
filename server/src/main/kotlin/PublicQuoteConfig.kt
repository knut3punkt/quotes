package no.esotericgames.quotes.server

import io.ktor.server.config.ApplicationConfig

/**
 * [maxFullQuoteWords] is the longest quote the TV shows in full. A longer quote is only shown through
 * one of its accepted excerpts, and is skipped while it has none. Keep it at least
 * `extraction.policy.minSourceWords`, or quotes too short to ever be extracted would never be shown.
 */
data class PublicQuoteConfig(val maxFullQuoteWords: Int = 55)

fun loadPublicQuoteConfig(rootConfig: ApplicationConfig): PublicQuoteConfig = PublicQuoteConfig(
    maxFullQuoteWords = rootConfig.propertyOrNull("publicQuotes.maxFullQuoteWords")?.getString()?.toInt()
        ?: PublicQuoteConfig().maxFullQuoteWords,
)
