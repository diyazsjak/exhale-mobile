package app.exhale.ui.screens

import androidx.compose.foundation.background
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
        Text(Fmt.money(state.savedMinor, s.currencyCode), style = ExType.display.copy(fontSize = 88.sp), color = c.ink)
        Text("kept in your pocket", style = ExType.titleItalic.copy(fontSize = 22.sp), color = c.sunInk)
        Text(
            "That's ${state.notSmoked} cigarettes you didn't light, against your old ${s.baselinePerDay} a day.",
            style = ExType.body, color = c.inkSoft,
        )
        val shown = 60
        val kept = if (state.notSmoked == 0) 0 else (shown * (state.notSmoked % 100) / 100f).toInt().coerceAtLeast(1)
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = ExSpace.xs).semantics { contentDescription = "Cigarettes not smoked" },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(shown) { i ->
                Box(Modifier.size(width = 6.dp, height = 26.dp).clip(ExRadius.pill).background(if (i < kept) c.sun else c.surface))
            }
        }
        Text("Each golden stick is one you didn't smoke. The row starts over every hundred.", style = ExType.caption, color = c.inkSoft)

        ExCard {
            Overline("Goal")
            Text(s.goalName, style = ExType.headline, color = c.ink)
            Box(Modifier.fillMaxWidth().padding(vertical = ExSpace.xs).height(12.dp).clip(ExRadius.pill).background(c.surfaceHigh)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(ExRadius.pill).background(Brush.horizontalGradient(listOf(c.sun, c.oxy))))
            }
            Text(
                "${Fmt.money(state.savedMinor, s.currencyCode)} of ${Fmt.money(s.goalMinor, s.currencyCode)}" +
                    when {
                        left == 0L -> " · reached. Go get it."
                        weeksLeft != null -> " · about $weeksLeft week${if (weeksLeft == 1) "" else "s"} at this pace"
                        else -> ""
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
