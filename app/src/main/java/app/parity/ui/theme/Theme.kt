package app.parity.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.parity.data.ThemeMode

/** Everything the screens colour themselves with. Material's scheme is derived from it so sheets and ripples agree. */
@Immutable
class Palette(
    val bg: Color,
    val key: Color,
    val keyText: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val subtext: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val isDark: Boolean,
)

private val Amoled = Palette(
    bg = Color(0xFF000000), key = Color(0xFF2B2B2B), keyText = Color(0xFFC6C6C8), accent = Color(0xFF5584F0),
    onAccent = Color(0xFFFFFFFF), text = Color(0xFFF2F2F4), subtext = Color(0xFF8E8E93),
    positive = Color(0xFF38D27F), negative = Color(0xFFFF6B66), warning = Color(0xFFFFB84D), isDark = true,
)
private val Dark = Palette(
    bg = Color(0xFF0F1013), key = Color(0xFF24262B), keyText = Color(0xFFC9CBD1), accent = Color(0xFF6B93F5),
    onAccent = Color(0xFFFFFFFF), text = Color(0xFFF1F2F5), subtext = Color(0xFF8C909A),
    positive = Color(0xFF38D27F), negative = Color(0xFFFF6B66), warning = Color(0xFFFFB84D), isDark = true,
)
private val Light = Palette(
    bg = Color(0xFFF3F4F7), key = Color(0xFFFFFFFF), keyText = Color(0xFF34363B), accent = Color(0xFF2F63E0),
    onAccent = Color(0xFFFFFFFF), text = Color(0xFF0E0F12), subtext = Color(0xFF6C7078),
    positive = Color(0xFF14A35C), negative = Color(0xFFD9423D), warning = Color(0xFFC77700), isDark = false,
)

private fun fromScheme(s: ColorScheme, dark: Boolean, base: Palette) = Palette(
    bg = s.surface, key = s.surfaceContainerHigh, keyText = s.onSurfaceVariant, accent = s.primary, onAccent = s.onPrimary,
    text = s.onSurface, subtext = s.onSurfaceVariant.copy(alpha = 0.8f),
    positive = base.positive, negative = base.negative, warning = base.warning, isDark = dark,
)

private fun materialFor(p: Palette): ColorScheme =
    (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
        surface = p.bg, onSurface = p.text, surfaceVariant = p.key, onSurfaceVariant = p.subtext,
        surfaceContainer = p.bg, surfaceContainerHigh = p.key, surfaceContainerHighest = p.key, outlineVariant = p.key,
        secondaryContainer = p.key, onSecondaryContainer = p.text,
    )

private val LocalPalette = staticCompositionLocalOf { Amoled }

object Pal {
    val current: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Amoled -> true
}

@Composable
fun ParityTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = resolveDark(mode)
    val ctx = LocalContext.current
    val base = when {
        !dark -> Light
        // "Auto" in the dark follows the screenshot: pure black, which is also what saves battery on OLED.
        mode == ThemeMode.Dark -> Dark
        else -> Amoled
    }
    val palette = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        val p = fromScheme(s, dark, base)
        // Keep true black under AMOLED even with Material You: only the accent comes from the wallpaper.
        if (dark && mode != ThemeMode.Dark) Palette(Amoled.bg, Amoled.key, Amoled.keyText, p.accent, p.onAccent, Amoled.text, Amoled.subtext, base.positive, base.negative, base.warning, true) else p
    } else base

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = materialFor(palette), typography = ParityType, content = content)
    }
}
