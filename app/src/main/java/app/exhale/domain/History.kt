package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One tracked day. [allowance] is null before the plan started: there was no allowance to be
 * within or over, so the day is shown neutral.
 */
data class DayTally(val date: LocalDate, val count: Int, val allowance: Int?) {
    val over: Boolean get() = allowance != null && count > allowance
}

/**
 * One calendar month of history. [days] has one entry per day of the month (index 0 = the 1st);
 * an entry is null when the day is before tracking began or still to come.
 */
data class MonthTally(
    val month: YearMonth,
    val days: List<DayTally?>,
    /** Average a day over the tracked days. */
    val average: Float,
    val tracked: Int,
    /** Tracked days that had an allowance (on or after the plan start). */
    val planned: Int,
    /** Planned days within the allowance. */
    val within: Int,
    val longestGap: Duration,
)

object History {

    /** Cigarettes per calendar day. */
    fun countsByDay(logs: List<SmokeLog>, zone: ZoneId): Map<LocalDate, Int> =
        logs.groupingBy { it.at.atZone(zone).toLocalDate() }.eachCount()

    /** One tracked day: its count, and its allowance (borrowing from the day before), none before the plan. */
    fun tally(s: UserSettings, counts: Map<LocalDate, Int>, date: LocalDate): DayTally =
        DayTally(
            date,
            counts[date] ?: 0,
            if (date.isBefore(s.planStart)) null else PlanMath.allowance(s, date, counts[date.minusDays(1)] ?: 0),
        )

    /**
     * Every month from the one holding [dataStart] to the one holding [today], newest first.
     * Today counts as tracked (its total so far). Empty when nothing has been tracked yet.
     */
    fun months(s: UserSettings, logs: List<SmokeLog>, today: LocalDate, now: Instant, zone: ZoneId, dataStart: LocalDate?): List<MonthTally> {
        if (dataStart == null || dataStart.isAfter(today)) return emptyList()
        val counts = countsByDay(logs, zone)
        val out = mutableListOf<MonthTally>()
        var m = YearMonth.from(today)
        val first = YearMonth.from(dataStart)
        while (!m.isBefore(first)) {
            val days = (1..m.lengthOfMonth()).map { d ->
                val date = m.atDay(d)
                if (date.isBefore(dataStart) || date.isAfter(today)) null else tally(s, counts, date)
            }
            val tracked = days.filterNotNull()
            val planned = tracked.filter { it.allowance != null }
            val start = m.atDay(1).atStartOfDay(zone).toInstant()
            val end = minOf(m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(), now)
            out += MonthTally(
                month = m,
                days = days,
                average = if (tracked.isEmpty()) 0f else tracked.sumOf { it.count }.toFloat() / tracked.size,
                tracked = tracked.size,
                planned = planned.size,
                within = planned.count { !it.over },
                longestGap = Stats.longestGapBetween(logs, maxOf(start, dataStart.atStartOfDay(zone).toInstant()), end),
            )
            m = m.minusMonths(1)
        }
        return out
    }
}
