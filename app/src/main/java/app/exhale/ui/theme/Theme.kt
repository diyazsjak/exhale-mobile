package app.exhale.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import app.exhale.domain.ThemeMode

private val LocalExColors = staticCompositionLocalOf { DarkColors }

/** Entry point to the design system from composables: `val c = Ex.colors`. */
object Ex {
    val colors: ExColors
        @Composable @ReadOnlyComposable get() = LocalExColors.current
}

/** Material's type roles mapped onto [ExType], for Material defaults such as a `Text` without a style. */
private val ExTypography = Typography(
    displayLarge = ExType.display, headlineLarge = ExType.title, headlineSmall = ExType.headline,
    bodyLarge = ExType.body, bodyMedium = ExType.body, bodySmall = ExType.caption,
    labelLarge = ExType.label, labelMedium = ExType.labelSmall, labelSmall = ExType.overline,
)

/** Material's colour roles mapped onto [ExColors], for Material defaults such as text selection. */
private fun materialScheme(c: ExColors) = (if (c.isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = c.primary, onPrimary = c.onPrimary, surfaceTint = c.primary, secondary = c.oxy, tertiary = c.sun,
    background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
    surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.inkSoft, outline = c.line, error = c.flare,
)

private val DarkScheme = materialScheme(DarkColors)
private val LightScheme = materialScheme(LightColors)

/** The palette for a theme. */
fun exColors(dark: Boolean): ExColors = if (dark) DarkColors else LightColors

/** Whether the app (and its widget) draw dark: the Appearance setting, or the phone's with "System". */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ExhaleTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = exColors(dark)
    CompositionLocalProvider(LocalExColors provides c) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = ExTypography) {
            CompositionLocalProvider(LocalContentColor provides c.ink, content = content)
        }
    }
}
