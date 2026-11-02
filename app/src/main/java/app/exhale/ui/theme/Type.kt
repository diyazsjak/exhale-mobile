package app.exhale.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.exhale.R

@OptIn(ExperimentalTextApi::class)
private fun newsreader(weight: Int, italic: Boolean = false) = Font(
    if (italic) R.font.newsreader_italic else R.font.newsreader,
    FontWeight(weight),
    if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", 48f),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    R.font.figtree,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Serif for headlines and big states. Calm, editorial. */
val SerifFamily = FontFamily(newsreader(400), newsreader(500), newsreader(400, italic = true), newsreader(500, italic = true))

/** Sans for everything you read or tap. */
val SansFamily = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))

/**
 * Type scale. Serif = what you feel (headlines, big states). Sans = what you read and tap.
 * Numbers that change (timers, counts) use the `number` styles: tabular digits, no jitter.
 */
object ExType {
    private val serif = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight(400), letterSpacing = (-0.01).em)
    private val sans = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight(400))

    val display = serif.copy(fontSize = 52.sp, lineHeight = 1.04.em)
    val title = serif.copy(fontSize = 34.sp, lineHeight = 1.12.em)
    val titleItalic = title.copy(fontStyle = FontStyle.Italic)
    val headline = serif.copy(fontSize = 24.sp, lineHeight = 1.2.em, fontWeight = FontWeight(500))

    val body = sans.copy(fontSize = 16.sp, lineHeight = 1.5.em)
    val bodyStrong = body.copy(fontWeight = FontWeight(600))
    val label = sans.copy(fontSize = 16.sp, lineHeight = 1.25.em, fontWeight = FontWeight(600))
    val labelSmall = sans.copy(fontSize = 14.sp, lineHeight = 1.25.em, fontWeight = FontWeight(500))
    val caption = sans.copy(fontSize = 13.sp, lineHeight = 1.4.em)
    val overline = sans.copy(fontSize = 12.sp, lineHeight = 1.3.em, fontWeight = FontWeight(600), letterSpacing = 0.08.em)

    val numberLarge = sans.copy(fontSize = 44.sp, lineHeight = 1.05.em, fontWeight = FontWeight(500), fontFeatureSettings = "tnum", letterSpacing = (-0.02).em)
    val number = sans.copy(fontSize = 28.sp, lineHeight = 1.1.em, fontWeight = FontWeight(500), fontFeatureSettings = "tnum")
    val numberSmall = sans.copy(fontSize = 15.sp, lineHeight = 1.3.em, fontWeight = FontWeight(500), fontFeatureSettings = "tnum")
}
