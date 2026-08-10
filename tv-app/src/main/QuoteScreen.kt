package no.esotericgames.quotes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import no.esotericgames.quotes.theme.TvQuotesTheme

/**
 * Placeholder full-screen quote display. Deliberately simple: one focusable card
 * showing a sample quote and its author, sized and spaced for TV viewing distance.
 */
@Composable
fun QuoteScreen(quote: Quote, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // TV-safe area: keep content clear of the overscan-prone screen edges.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        contentAlignment = Alignment.Center,
    ) {
        val focusRequester = remember { FocusRequester() }
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        // The screen has exactly one focusable element, so it should already be
        // focused when a D-pad first arrives — there is nothing else to focus.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .focusRequester(focusRequester)
                .focusable(interactionSource = interactionSource)
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.boilerplate_notice).uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "“${quote.text}”",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = formatAttribution(quote.author),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(device = "spec:width=1920px,height=1080px,dpi=320", showBackground = true)
@Composable
private fun QuoteScreenPreview() {
    TvQuotesTheme {
        QuoteScreen(sampleQuote)
    }
}
