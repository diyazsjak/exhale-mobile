package app.exhale.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.Eyebrow
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val Bronze = Color(0xFF5A4310)

/** The one loud screen: flat sun-yellow, breaking the dark world on purpose. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyScreen(state: ExhaleState) {
    val s = state.settings
    val days = Duration.between(s.planStart.atStartOfDay(ZoneId.systemDefault()).toInstant(), state.now).toMillis() / 86_400_000.0
    val perDay = if (days > 0.5) state.savedMinor / days else 0.0
    val left = (s.goalMinor - state.savedMinor).coerceAtLeast(0)
    val weeksLeft = if (perDay > 0) ceil(left / perDay / 7).toInt() else null
    val progress = if (s.goalMinor > 0) (state.savedMinor.toFloat() / s.goalMinor).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxSize()
            .background(Ex.Sun)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Eyebrow("Not burned · since ${s.planStart.format(DateTimeFormatter.ofPattern("MMM d"))}", color = Bronze)
        Text(Fmt.money(state.savedMinor, s.currencyCode), style = ExType.hero.copy(fontSize = 88.sp), color = Ex.Void)
        Text("That's ${state.notSmoked} cigarettes you didn't light, against your old ${s.baselinePerDay} a day.", style = ExType.body, color = Bronze)

        val shown = 80
        val faded = if (state.notSmoked == 0) 0 else (shown * (state.notSmoked % 100) / 100f).toInt().coerceAtLeast(1)
        FlowRow(
            Modifier.fillMaxWidth().semantics { contentDescription = "Cigarettes not smoked" },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(shown) { i ->
                Box(Modifier.size(width = 5.dp, height = 24.dp).clip(RoundedCornerShape(1.dp)).background(Ex.Void.copy(alpha = if (i < faded) 0.15f else 0.85f)))
            }
        }
        Text("Each faded stick is one not smoked. Every hundred, the row resets.", style = ExType.small, color = Bronze)

        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ex.Void).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("Goal · ${s.goalName}")
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ex.Line)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Brush.horizontalGradient(listOf(Ex.Sky, Ex.Oxy))))
            }
            Text(
                "${Fmt.money(state.savedMinor, s.currencyCode)} / ${Fmt.money(s.goalMinor, s.currencyCode)}" +
                    when {
                        left == 0L -> " · reached. Go get it."
                        weeksLeft != null -> " · about $weeksLeft week${if (weeksLeft == 1) "" else "s"} at this pace"
                        else -> ""
                    },
                style = ExType.dataSmall, color = Ex.Mute,
            )
        }
        Text(
            "Based on ${Fmt.money(s.packPriceMinor, s.currencyCode, decimals = true)} for ${s.cigsPerPack}. Change it in Settings.",
            style = ExType.small, color = Bronze,
        )
    }
}
