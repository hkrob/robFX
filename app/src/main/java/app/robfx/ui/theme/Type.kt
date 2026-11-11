package app.robfx.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.robfx.R

/** Lato, as in the reference screenshot. Bundled, so it renders the same offline and on every device. */
val Lato = FontFamily(
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
)

private fun s(size: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) =
    TextStyle(fontFamily = Lato, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp)

val RobFxType = Typography(
    displayLarge = s(56, FontWeight.Light), displayMedium = s(48, FontWeight.Light), displaySmall = s(40, FontWeight.Light),
    headlineLarge = s(32, FontWeight.Light), headlineMedium = s(28, FontWeight.Light), headlineSmall = s(24, FontWeight.Normal),
    titleLarge = s(22, FontWeight.Normal), titleMedium = s(17, FontWeight.Bold), titleSmall = s(14, FontWeight.Bold),
    bodyLarge = s(17), bodyMedium = s(15), bodySmall = s(13),
    labelLarge = s(14, FontWeight.Bold, 0.2), labelMedium = s(12, FontWeight.Bold, 0.3), labelSmall = s(11, FontWeight.Bold, 0.4),
)
