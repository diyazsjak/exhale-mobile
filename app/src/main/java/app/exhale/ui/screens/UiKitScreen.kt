package app.exhale.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.exhale.domain.ThemeMode
import app.exhale.domain.Trigger
import app.exhale.ui.components.ActiveFilter
import app.exhale.ui.components.ButtonSize
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.DayDot
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExChip
import app.exhale.ui.components.ExDialog
import app.exhale.ui.components.ExIconButton
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExLink
import app.exhale.ui.components.LegendItem
import app.exhale.ui.components.MonthCalendar
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.ExTextField
import app.exhale.ui.components.FloatingTabBar
import app.exhale.ui.components.GradientRing
import app.exhale.ui.components.HeaderLabel
import app.exhale.ui.components.ListRow
import app.exhale.ui.components.OptionCard
import app.exhale.ui.components.Overline
import app.exhale.ui.components.SegmentedControl
import app.exhale.ui.components.Stat
import app.exhale.ui.components.StepProgress
import app.exhale.ui.components.Stepper
import app.exhale.ui.components.TabItem
import app.exhale.ui.components.TriggerChips
import app.exhale.ui.components.WeekStrip
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Living catalogue of the design system: every token and component, interactive, in the
 * current theme. Opened from Settings → Prototype tools → Design system.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UiKitScreen(theme: ThemeMode, onTheme: (ThemeMode) -> Unit, onClose: () -> Unit) {
    val c = Ex.colors
    ExScreen(
        bottomClearance = ExSpace.xl,
        header = {
            ExIconButton(ExIcons.Back, "Back", onClose)
            HeaderLabel("Design system")
            Spacer(Modifier.size(ExSize.iconButton))
        },
    ) {
        Text("Exhale UI kit", style = ExType.title, color = c.ink)
        SegmentedControl(ThemeMode.entries.map { it.label }, theme.ordinal, { onTheme(ThemeMode.entries[it]) })

        Section("Colour · neutrals")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm), verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            Swatch("bg", c.bg); Swatch("surface", c.surface); Swatch("surfaceHigh", c.surfaceHigh); Swatch("line", c.line)
            Swatch("ink", c.ink); Swatch("inkSoft", c.inkSoft); Swatch("inkFaint", c.inkFaint); Swatch("primary", c.primary)
        }
        Section("Colour · meaning")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm), verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            Swatch("ember · smoke", c.ember); Swatch("flare · craving", c.flare); Swatch("sky · between", c.sky)
            Swatch("oxy · recovery", c.oxy); Swatch("sun · money", c.sun)
        }
        Text("Heat scale, 1 → 0", style = ExType.caption, color = c.inkSoft)
        Row(Modifier.fillMaxWidth().height(28.dp).clip(ExRadius.pill)) {
            for (i in 10 downTo 0) Box(Modifier.weight(1f).height(28.dp).background(c.heat(i / 10f)))
        }

        Section("Type")
        TypeRow("display · Newsreader 52", ExType.display, "2h 41m")
        TypeRow("title · Newsreader 34", ExType.title, "When you light up")
        TypeRow("titleItalic", ExType.titleItalic, "since your last one")
        TypeRow("headline · Newsreader 24", ExType.headline, "Logged")
        TypeRow("headlineItalic", ExType.headlineItalic, "then I ride one wave first.")
        TypeRow("body · Figtree 16", ExType.body, "Going over is borrowed from tomorrow.")
        TypeRow("label · Figtree 16 semibold", ExType.label, "Continue")
        TypeRow("caption · Figtree 13", ExType.caption, "Hold the ember to log.")
        TypeRow("overline · Figtree 12", ExType.overline, "REGULATE · WEEK 3")
        TypeRow("numberLarge · tabular", ExType.numberLarge, "00:38:12")

        Section("Buttons")
        ExButton("Primary · large", {}, Modifier.fillMaxWidth())
        ExButton("Secondary", {}, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = ExIcons.Wave)
        ExButton("Accent", {}, Modifier.fillMaxWidth(), style = ButtonStyle.Accent)
        ExCard(padding = ExSpace.sm) { ExButton("Tonal · inside a card", {}, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal, size = ButtonSize.Medium) }
        ExButton("Disabled", {}, Modifier.fillMaxWidth(), enabled = false)
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            ExButton("Medium", {}, size = ButtonSize.Medium)
            ExButton("Ghost", {}, style = ButtonStyle.Ghost, size = ButtonSize.Medium)
            ExIconButton(ExIcons.Settings, "Icon button", {})
            ExIconButton(ExIcons.Close, "Icon button, plain", {}, filled = false)
        }
        ExLink("Text link", {})

        Section("Chips & badges")
        var chip by remember { mutableStateOf<Trigger?>(Trigger.COFFEE) }
        TriggerChips(chip, { chip = if (chip == it) null else it })
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
            ExChip("Sky", true, {}, accent = c.sky); ExChip("Oxy", true, {}, accent = c.oxy); ExChip("Off", false, {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
            ExBadge("7 of 12 today"); ExBadge("over", accent = c.flare); ExBadge("+3 days", accent = c.oxy)
        }

        Section("Inputs")
        var text by remember { mutableStateOf("") }
        ExTextField("Text field", text, { text = it }, placeholder = "Placeholder")
        var capped by remember { mutableStateOf("New headphones") }
        ExTextField("With a length cap", capped, { capped = it }, maxLength = 40)
        var n by remember { mutableIntStateOf(12) }
        Stepper(n, { n = it }, 0..60, "stepper")
        var seg by remember { mutableIntStateOf(0) }
        SegmentedControl(listOf("One", "Two", "Three"), seg, { seg = it })
        ActiveFilter("Sunday · 10, 2 borrowed", "Back to 30 days", {})

        Section("Selection & lists")
        var opt by remember { mutableIntStateOf(0) }
        OptionCard("Option card", "Selected state with accent ring", opt == 0, { opt = 0 }, trailing = "13 → 12 → 11")
        OptionCard("Option card", "Unselected state", opt == 1, { opt = 1 })
        ExCard(padding = ExSpace.sm) { ListRow("List row", {}, value = "Value"); ListRow("Another row", {}) }

        Section("Cards & stats")
        ExCard {
            Overline("Card · overline")
            Row { Stat("13", "a day this week", Modifier.weight(1f)); Stat("12", "next week", Modifier.weight(1f), color = c.oxyInk) }
        }
        var dialog by remember { mutableStateOf(false) }
        ExButton("Open a dialog", { dialog = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, size = ButtonSize.Medium)
        if (dialog) ExDialog({ dialog = false }) { Overline("Saving for"); Text("A weekend trip to the mountains with my family", style = ExType.headline, color = c.ink) }
        var lit by remember { mutableStateOf(true) }
        ExCard(onClick = { lit = !lit }, outlined = lit) { Text("Tappable card, outlined while on", style = ExType.body, color = c.ink) }

        Section("Progress & data")
        StepProgress(2, 4, Modifier.fillMaxWidth())
        GradientRing(0.62f, listOf(c.sky, c.oxy), Modifier.padding(horizontal = 64.dp), stroke = 20.dp) {
            Text("62%", style = ExType.number, color = c.ink)
        }
        ExCard(color = c.tint(c.sun)) {
            GradientRing(0.31f, listOf(c.sun, c.sun), Modifier.size(104.dp), stroke = 12.dp, track = c.tint(c.sun)) {
                Text("31%", style = ExType.numberSmall, color = c.ink)
            }
        }
        val today = LocalDate.now()
        WeekStrip((0..6).map { i -> val d = today.minusDays((6 - i).toLong()); DayDot(d, listOf(9, 11, 8, 14, 10, 7, 5)[i], 10) }, today)
        val month = YearMonth.from(today)
        MonthCalendar(
            month,
            (1..month.lengthOfMonth()).map { d ->
                val date = month.atDay(d)
                when {
                    date.isAfter(today) -> DayDot(date, null, null)
                    d < 3 -> DayDot(date, null, null)
                    d < 5 -> DayDot(date, 12, null)
                    else -> DayDot(date, 8 + (d * 7) % 5, 10)
                }
            },
            today,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.md)) {
            LegendItem(c.oxy, "Within allowance"); LegendItem(c.ember, "Borrowed")
        }

        Section("Icons")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ExSpace.md), verticalArrangement = Arrangement.spacedBy(ExSpace.md)) {
            listOf(
                ExIcons.Ember, ExIcons.Regulate, ExIcons.Rhythm, ExIcons.Air, ExIcons.Money, ExIcons.Wave, ExIcons.Plus,
                ExIcons.Check, ExIcons.Close, ExIcons.Back, ExIcons.Forward, ExIcons.Settings, ExIcons.Undo, ExIcons.Sun,
                ExIcons.Moon, ExIcons.Palette,
            ).forEach { Icon(it, null, Modifier.size(28.dp), tint = c.ink) }
        }

        Section("Floating tab bar")
        var tab by remember { mutableIntStateOf(0) }
        Box(Modifier.fillMaxWidth().clip(ExRadius.lg).border(1.dp, c.line, ExRadius.lg)) {
            FloatingTabBar(
                listOf(
                    TabItem(ExIcons.Ember, "Ember", c.ember),
                    TabItem(ExIcons.Rhythm, "Rhythm", c.flare), TabItem(ExIcons.Air, "Air", c.oxy), TabItem(ExIcons.Money, "Money", c.sun),
                ),
                tab, { tab = it },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Overline(title, Modifier.padding(top = ExSpace.lg))
}

@Composable
private fun Swatch(name: String, color: Color) {
    val c = Ex.colors
    Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(56.dp).clip(ExRadius.sm).background(color).border(1.dp, c.line, ExRadius.sm))
        Text(name, style = ExType.caption, color = c.ink)
        Text("#%06X".format(color.toArgb() and 0xFFFFFF), style = ExType.caption, color = c.inkFaint)
    }
}

@Composable
private fun TypeRow(name: String, style: TextStyle, sample: String) {
    val c = Ex.colors
    Column {
        Text(name, style = ExType.caption, color = c.inkFaint)
        Text(sample, style = style, color = c.ink)
    }
}
