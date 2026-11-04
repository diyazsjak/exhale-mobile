package app.exhale.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.exhale.domain.History
import app.exhale.ui.ExhaleState
import app.exhale.ui.Fmt
import app.exhale.ui.components.DayDot
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExIconButton
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.HeaderLabel
import app.exhale.ui.components.LegendItem
import app.exhale.ui.components.MonthCalendar
import app.exhale.ui.components.ScreenTitle
import app.exhale.ui.components.Stat
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every tracked day as a calendar, newest month first, opened from Rhythm. Each month ends with
 * its average, days within the allowance and longest gap. Tapping a day opens it on Rhythm.
 */
@Composable
fun HistoryScreen(state: ExhaleState, onPickDay: (LocalDate) -> Unit, onClose: () -> Unit) {
    val c = Ex.colors
    val zone = ZoneId.systemDefault()
    val minute = state.now.epochSecond / 60
    val months = remember(state.logs, state.settings, state.today, state.dataStart, minute) {
        History.months(state.settings, state.logs, state.today, state.now, zone, state.dataStart)
    }
    val start = state.dataStart
    ExScreen(
        bottomClearance = ExSpace.xl,
        header = {
            ExIconButton(ExIcons.Back, "Back", onClose)
            HeaderLabel("History")
            Spacer(Modifier.size(ExSize.iconButton))
        },
    ) {
        ScreenTitle(
            "Every day so far",
            subtitle = if (start == null) "Nothing logged yet. Your days will show up here."
            else "Since ${start.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))}. Tap a day to see it on Rhythm.",
        )
        if (months.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.md)) {
                LegendItem(c.oxy, "Within allowance")
                LegendItem(c.ember, "Borrowed from the next day")
            }
        }
        months.forEach { m ->
            Column(Modifier.padding(top = ExSpace.xs), verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
                // The start date is in the subtitle and the day count in the summary below.
                Text(monthName(m.month, state.today), style = ExType.headline, color = c.ink, modifier = Modifier.semantics { heading() })
                MonthCalendar(
                    m.month,
                    m.days.mapIndexed { i, d -> DayDot(m.month.atDay(i + 1), d?.count, d?.allowance) },
                    state.today,
                    onSelect = onPickDay,
                )
                ExCard(padding = ExSpace.md) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
                        // The gap ("10h 12m") is the widest value, so it gets the most room.
                        Stat("%.1f".format(m.average), "a day on average", Modifier.weight(0.9f))
                        if (m.planned > 0) {
                            Stat("${m.within}", "of ${m.planned} days within your allowance", Modifier.weight(1f), color = c.oxyInk)
                        }
                        Stat(Fmt.gap(m.longestGap), "longest gap", Modifier.weight(1.3f), color = c.oxyInk)
                    }
                }
            }
        }
    }
}

/** "October", or "October 2025" outside the current year. */
private fun monthName(m: YearMonth, today: LocalDate): String =
    m.format(DateTimeFormatter.ofPattern(if (m.year == today.year) "LLLL" else "LLLL yyyy", Locale.getDefault()))
