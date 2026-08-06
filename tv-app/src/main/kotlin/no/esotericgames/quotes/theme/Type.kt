package no.esotericgames.quotes.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// TV screens are viewed from several meters away, so the default type scale is
// scaled up for the two styles this boilerplate actually uses.
val TvQuotesTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 56.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium, fontSize = 24.sp),
    )
}
