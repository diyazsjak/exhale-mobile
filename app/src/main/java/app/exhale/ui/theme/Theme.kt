package app.exhale.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.exhale.R

/**
 * "Two temperatures": warm hues mean smoke, cool hues mean recovery.
 * Never use Ember/Flare for success or Oxygen for a cigarette.
 */
object Ex {
    val Void = Color(0xFF0D0B1C)
    val Panel = Color(0xFF17142D)
    val Line = Color(0xFF2C2750)
    val Ink = Color(0xFFF4EFFF)
    val Mute = Color(0xFFA49DCB)
    val Ember = Color(0xFFFF5A1F)
    val Flare = Color(0xFFFF2E88)
    val Sky = Color(0xFF6C7BFF)
    val Oxy = Color(0xFF2EF2D0)
    val Sun = Color(0xFFFFC53D)
    val Glow = Color(0xFFFFE2A8)

    /** heat 1 = ember, 0.5 = sky, 0 = oxygen. */
    fun heatColor(heat: Float): Color = when {
        heat >= 0.5f -> lerp(Sky, Ember, (heat - 0.5f) * 2f)
        else -> lerp(Oxy, Sky, heat * 2f)
    }

    fun heatAccent(heat: Float): Color = when {
        heat >= 0.5f -> lerp(Sky, Flare, (heat - 0.5f) * 2f)
        else -> lerp(Oxy, Sky, heat * 2f)
    }
}

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, width: Float) = Font(
    R.font.bricolage_grotesque,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

@OptIn(ExperimentalTextApi::class)
private fun hanken(weight: Int) = Font(
    R.font.hanken_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun martian(weight: Int) = Font(
    R.font.martian_mono,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(87.5f)),
)

val DisplayFamily = FontFamily(bricolage(800, 75f), bricolage(700, 85f))
val BodyFamily = FontFamily(hanken(400), hanken(500), hanken(600))
val DataFamily = FontFamily(martian(400), martian(600))

object ExType {
    val hero = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(800), fontSize = 64.sp, lineHeight = 0.92.em, letterSpacing = (-0.02).em)
    val title = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(700), fontSize = 34.sp, lineHeight = 1.0.em, letterSpacing = (-0.01).em)
    val heading = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 1.1.em)
    val body = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 1.5.em)
    val bodyStrong = body.copy(fontWeight = FontWeight(600))
    val small = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 1.4.em)
    val eyebrow = TextStyle(fontFamily = DataFamily, fontWeight = FontWeight(400), fontSize = 10.sp, letterSpacing = 0.14.em)
    val data = TextStyle(fontFamily = DataFamily, fontWeight = FontWeight(600), fontSize = 28.sp)
    val dataSmall = TextStyle(fontFamily = DataFamily, fontWeight = FontWeight(400), fontSize = 12.sp)
}

@Composable
fun ExhaleTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Ex.Oxy,
        onPrimary = Ex.Void,
        secondary = Ex.Ember,
        onSecondary = Ex.Void,
        tertiary = Ex.Sun,
        background = Ex.Void,
        onBackground = Ex.Ink,
        surface = Ex.Panel,
        onSurface = Ex.Ink,
        surfaceVariant = Ex.Panel,
        onSurfaceVariant = Ex.Mute,
        outline = Ex.Line,
        error = Ex.Flare,
    )
    val typo = Typography(
        bodyLarge = ExType.body,
        bodyMedium = ExType.body.copy(fontSize = 14.sp),
        bodySmall = ExType.small,
        labelLarge = ExType.bodyStrong.copy(fontSize = 14.sp),
        titleLarge = ExType.heading,
        headlineLarge = ExType.title,
    )
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}
