package no.esotericgames.quotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import no.esotericgames.quotes.theme.TvQuotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvQuotesTheme {
                QuoteScreen(quote = sampleQuote)
            }
        }
    }
}
