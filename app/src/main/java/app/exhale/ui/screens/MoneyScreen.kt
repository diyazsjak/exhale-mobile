package app.exhale.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.exhale.domain.MoneySummary
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExDialog
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.GradientRing
import app.exhale.ui.components.Overline
import app.exhale.ui.components.Stat
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToLong

/** Most pack icons drawn; beyond that the row ends in "+N". */
private const val MAX_PACK_ICONS = 39

/**
 * Money: what not smoking has kept, since the plan started. Amount, the last 7 and 30 days, packs
 * not bought, where the current pace leads, and the goal. All numbers come from [MoneySummary].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyScreen(state: ExhaleState, scrollState: ScrollState = rememberScrollState()) {
    val c = Ex.colors
    val s = state.settings
    val m = state.money ?: return
    val code = s.currencyCode
    val money = { minor: Long -> Fmt.money(minor, code) }

    ExScreen(underAppBar = true, scrollState = scrollState) {
        // One line at any size: the digits, then the currency symbol or code, smaller, after them
        // ("12,345 KZT", "62 $"). The whole amount steps down from 88sp if it still doesn't fit.
        Column(verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
            BasicText(
                text = moneyHero(m.keptMinor, code),
                style = ExType.display.copy(color = c.ink),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 88.sp, stepSize = 2.sp),
            )
            Text("kept since you started", style = ExType.titleItalic.copy(fontSize = 22.sp), color = c.sunInk)
            Text(
                if (m.notSmoked > 0) "You used to smoke ${s.baselinePerDay} a day. Since you started, you've smoked ${m.notSmoked} fewer than that."
                else "You used to smoke ${s.baselinePerDay} a day. Every one you skip from here adds up.",
                style = ExType.body, color = c.inkSoft,
            )
            // The last 7 and 30 days, once the plan is older than that (until then they equal the total).
            if (m.last7Minor != null || m.last30Minor != null) {
                FlowRow(
                    Modifier.padding(top = ExSpace.xxs),
                    horizontalArrangement = Arrangement.spacedBy(ExSpace.xs),
                    verticalArrangement = Arrangement.spacedBy(ExSpace.xs),
                ) {
                    m.last7Minor?.let { ExBadge("${money(it)} in the last 7 days", accent = c.sun) }
                    m.last30Minor?.let { ExBadge("${money(it)} in the last 30 days", accent = c.sun) }
                }
            }
        }

        PacksCard(m)

        if (m.weeklyMinor > 0) {
            ExCard {
                Overline("At this pace")
                Text(
                    "If the next weeks go like ${if (m.paceFromLastWeek) "your last 7 days" else "your days so far"} " +
                        "(${money(m.weeklyMinor)} a week), you'll keep:",
                    style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(bottom = ExSpace.xs),
                )
                listOf("In a month" to 30, "In 6 months" to 182, "In a year" to 365).forEach { (label, days) ->
                    Box(Modifier.fillMaxWidth().height(ExSize.hairline).background(c.surfaceHigh))
                    Row(Modifier.fillMaxWidth().padding(vertical = ExSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = ExType.body, color = c.ink, modifier = Modifier.weight(1f))
                        Text("+${money(m.projectedMinor(days))}", style = ExType.numberSmall.copy(fontSize = 18.sp), color = c.sunInk)
                    }
                }
            }
        }

        GoalCard(s.goalName, money(s.goalMinor), m, money)

        Text(
            "A pack of ${s.cigsPerPack} costs ${Fmt.money(s.packPriceMinor, code, decimals = true)}, so each one is " +
                "${Fmt.money(s.pricePerCigMinor.roundToLong(), code, decimals = true)}. Change it in Settings.",
            style = ExType.caption, color = c.inkFaint,
        )
    }
}

/** "18 packs you didn't buy": one icon per pack, the next one filling up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PacksCard(m: MoneySummary) {
    val c = Ex.colors
    val full = minOf(m.packs, MAX_PACK_ICONS)
    val more = m.packs - full
    ExCard {
        if (m.packs > 0) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
                Text("${m.packs}", style = ExType.number, color = c.sunInk)
                Text(if (m.packs == 1) "pack you didn't buy" else "packs you didn't buy", style = ExType.body, color = c.ink, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = ExSpace.xs).semantics {
                contentDescription = "${m.packs} packs not bought, and ${m.packRemainder} of ${m.perPack} into the next"
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(full) { PackIcon(1f) }
            if (more > 0) {
                Box(Modifier.size(width = 38.dp, height = 38.dp), contentAlignment = Alignment.Center) {
                    Text("+$more", style = ExType.numberSmall, color = c.sunInk)
                }
            } else {
                PackIcon(m.packRemainder / m.perPack.toFloat())
            }
        }
        Text(
            if (m.packs == 0) "Your first pack is filling up: ${m.packRemainder} of ${m.perPack}."
            else "The last one is filling up: ${m.packRemainder} of ${m.perPack}.",
            style = ExType.caption, color = c.inkSoft,
        )
    }
}

/** One pack, filled from the bottom by [fill] 0..1. */
@Composable
private fun PackIcon(fill: Float) {
    val c = Ex.colors
    Box(Modifier.size(width = 28.dp, height = 38.dp).clip(ExRadius.xs).background(c.surfaceHigh), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(fill.coerceIn(0f, 1f)).background(c.sun))
        // The lid line.
        Box(Modifier.align(Alignment.TopCenter).padding(top = 7.dp).fillMaxWidth().height(2.dp).background(c.bg.copy(alpha = 0.35f)))
    }
}

/**
 * The goal: progress ring, what it's for and its price, then what's left and how long at this pace.
 * A name too long for two lines ends in "…"; then the card opens the full name in a dialog.
 */
@Composable
private fun GoalCard(name: String, price: String, m: MoneySummary, money: (Long) -> String) {
    val c = Ex.colors
    var cut by remember(name) { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val reached = m.goalLeftMinor == 0L
    val percent = (m.goalProgress * 100).toInt()
    ExCard(
        color = c.tint(c.sun),
        onClick = if (cut) ({ open = true }) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            GradientRing(
                m.goalProgress, listOf(c.sun, c.sun), Modifier.size(104.dp), stroke = 12.dp, track = c.tint(c.sun),
                contentDescription = "$percent% of the way",
            ) {
                Text("$percent%", style = ExType.numberSmall.copy(fontSize = 20.sp), color = c.ink, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Overline("Saving for", color = c.sunInk)
                Text(
                    name, style = ExType.headline, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) cut = true },
                )
                Text(price, style = ExType.caption, color = c.inkSoft)
            }
        }
        Box(Modifier.padding(top = ExSpace.sm).fillMaxWidth().height(ExSize.hairline).background(c.tint(c.sun)))
        Row(Modifier.padding(top = ExSpace.xs)) {
            if (reached) {
                Stat("Reached", "go get it", Modifier.weight(1f), color = c.sunInk)
            } else {
                Stat(money(m.goalLeftMinor), "still to go", Modifier.weight(1f))
                m.weeksToGoal?.let { w -> Stat("~$w week${if (w == 1) "" else "s"}", "at this pace", Modifier.weight(1f)) }
            }
        }
    }
    if (open) {
        ExDialog(onDismiss = { open = false }) {
            Overline("Saving for", color = c.sunInk)
            Text(name, style = ExType.headline, color = c.ink)
            Text(price, style = ExType.caption, color = c.inkSoft)
        }
    }
}

/** Digits at full size, then the currency symbol or code at 45%, on the same baseline. */
private fun moneyHero(minor: Long, currencyCode: String): AnnotatedString {
    val number = NumberFormat.getIntegerInstance(Locale.getDefault()).format(minor / 100)
    val symbol = runCatching { Currency.getInstance(currencyCode).getSymbol(Locale.getDefault()) }.getOrDefault(currencyCode)
    return buildAnnotatedString {
        append(number)
        withStyle(SpanStyle(fontSize = 0.45.em)) { append(" ") ; append(symbol) }
    }
}
