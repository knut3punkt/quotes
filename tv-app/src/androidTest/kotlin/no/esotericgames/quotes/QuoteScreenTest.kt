package no.esotericgames.quotes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import no.esotericgames.quotes.theme.TvQuotesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuoteScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun quoteTextAndAuthorAreDisplayed() {
        composeTestRule.setContent {
            TvQuotesTheme {
                QuoteScreen(sampleQuote)
            }
        }

        composeTestRule.onNodeWithText("“${sampleQuote.text}”").assertIsDisplayed()
        composeTestRule.onNodeWithText(formatAttribution(sampleQuote.author)).assertIsDisplayed()
    }
}
