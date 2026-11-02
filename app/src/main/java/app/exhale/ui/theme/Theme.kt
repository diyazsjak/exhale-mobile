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

val LocalExColors = staticCompositionLocalOf { DarkColors }

/** Entry point to the design system from composables: `val c = Ex.colors`. */
object Ex {
    val colors: ExColors
        @Composable @ReadOnlyComposable get() = LocalExColors.current
}

@Composable
fun ExhaleTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, secondary = c.oxy, tertiary = c.sun,
            background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.inkSoft, outline = c.line, error = c.flare,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, secondary = c.oxy, tertiary = c.sun,
            background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.inkSoft, outline = c.line, error = c.flare,
        )
    }
    val typography = Typography(
        displayLarge = ExType.display, headlineLarge = ExType.title, headlineSmall = ExType.headline,
        bodyLarge = ExType.body, bodyMedium = ExType.body, bodySmall = ExType.caption,
        labelLarge = ExType.label, labelMedium = ExType.labelSmall, labelSmall = ExType.overline,
    )
    CompositionLocalProvider(LocalExColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            CompositionLocalProvider(LocalContentColor provides c.ink, content = content)
        }
    }
}
