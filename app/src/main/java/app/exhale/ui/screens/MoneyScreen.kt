package app.exhale.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.Overline
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.ceil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyScreen(state: ExhaleState) {
    val c = Ex.colors
    val s = state.settings
    val days = Duration.between(s.planStart.atStartOfDay(ZoneId.systemDefault()).toInstant(), state.now).toMillis() / 86_400_000.0
    val perDay = if (days > 0.5) state.savedMinor / days else 0.0
    val left = (s.goalMinor - state.savedMinor).coerceAtLeast(0)
    val weeksLeft = if (perDay > 0) ceil(left / perDay / 7).toInt() else null
    val progress = if (s.goalMinor > 0) (state.savedMinor.toFloat() / s.goalMinor).coerceIn(0f, 1f) else 0f

    ExScreen(header = { Overline("Not burned · since ${s.planStart.format(DateTimeFormatter.ofPattern("MMM d"))}") }) {
        // One line at any size: the digits, then the currency symbol or code, smaller, after them
        // ("12,345 KZT", "62 $"). The whole amount steps down from 88sp if it still doesn't fit.
        BasicText(
            text = moneyHero(state.savedMinor, s.currencyCode),
            style = ExType.display.copy(color = c.ink),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 88.sp, stepSize = 2.sp),
        )
        Text("kept in your pocket", style = ExType.titleItalic.copy(fontSize = 22.sp), color = c.sunInk)
        Text(
            "That's ${state.notSmoked} cigarettes you didn't light, against your old ${s.baselinePerDay} a day.",
            style = ExType.body, color = c.inkSoft,
        )
        val shown = 60
        val kept = if (state.notSmoked == 0) 0 else (shown * (state.notSmoked % 100) / 100f).toInt().coerceAtLeast(1)
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = ExSpace.xs).semantics { contentDescription = "${state.notSmoked % 100} of the next hundred not smoked" },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(shown) { i ->
                Box(Modifier.size(width = 6.dp, height = 26.dp).clip(ExRadius.pill).background(if (i < kept) c.sun else c.surface))
            }
        }
        // The row is progress to the next hundred (60 sticks stand for 100), not one stick each.
        Text("${state.notSmoked % 100} toward your next hundred.", style = ExType.caption, color = c.inkSoft)

        ExCard {
            Overline("Goal")
            Text(s.goalName, style = ExType.headline, color = c.ink)
            Box(Modifier.fillMaxWidth().padding(vertical = ExSpace.xs).height(12.dp).clip(ExRadius.pill).background(c.surfaceHigh)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(ExRadius.pill).background(Brush.horizontalGradient(listOf(c.sun, c.oxy))))
            }
            Text(
                // The amount kept is the hero above; here say what's still to go.
                when {
                    left == 0L -> "Reached. Go get it."
                    weeksLeft != null -> "${Fmt.money(left, s.currencyCode)} to go · about $weeksLeft week${if (weeksLeft == 1) "" else "s"} at this pace"
                    else -> "${Fmt.money(left, s.currencyCode)} to go"
                },
                style = ExType.numberSmall, color = c.inkSoft,
            )
        }
        Text(
            "Based on ${Fmt.money(s.packPriceMinor, s.currencyCode, decimals = true)} for ${s.cigsPerPack}. Change it in Settings.",
            style = ExType.caption, color = c.inkFaint,
        )
    }
}

/** Digits at full size, then the currency symbol or code at 45%, on the same baseline. */
private fun moneyHero(minor: Long, currencyCode: String): AnnotatedString {
    val number = NumberFormat.getIntegerInstance(Locale.getDefault()).format(minor / 100)
    val symbol = runCatching { Currency.getInstance(currencyCode).getSymbol(Locale.getDefault()) }.getOrDefault(currencyCode)
    return buildAnnotatedString {
        append(number)
        withStyle(SpanStyle(fontSize = 0.45.em)) { append("\u2009") ; append(symbol) }
    }
}
