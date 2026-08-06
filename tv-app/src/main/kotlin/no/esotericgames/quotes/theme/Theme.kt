package no.esotericgames.quotes.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvQuotesColorScheme = darkColorScheme(
    background = QuoteBackground,
    surface = QuoteSurface,
    primary = QuoteAccent,
    onBackground = QuoteTextPrimary,
    onSurface = QuoteTextPrimary,
    onSurfaceVariant = QuoteTextSecondary,
)

@Composable
fun TvQuotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvQuotesColorScheme,
        typography = TvQuotesTypography,
        content = content,
    )
}
