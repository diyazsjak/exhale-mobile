package app.exhale.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/**
 * The daily allowance plan. No Android types, fully unit-tested.
 *
 * - Allowance tapers weekly from [UserSettings.startAllowance] according to [TaperType].
 * - Smoking over the allowance borrows from the next day (debt), never more than one day back.
 * - The interval spreads the day's allowance over waking minutes.
 */
object PlanMath {

    /** Whole weeks from [start] to [date]; 0 before the start. */
    fun weeksIntoPlan(start: LocalDate, date: LocalDate): Int =
        ChronoUnit.WEEKS.between(start, date).toInt().coerceAtLeast(0)

    /** The allowance on [date] from the taper alone, before any borrowing. */
    fun baseAllowance(s: UserSettings, date: LocalDate): Int {
        val weeks = weeksIntoPlan(s.planStart, date)
        val start = s.startAllowance.coerceAtLeast(0)
        return when (s.taper) {
            TaperType.HOLD -> start
            TaperType.MINUS_ONE_WEEKLY -> max(0, start - weeks)
            // Always move at least one per week so the plan never stalls on small numbers.
            TaperType.TEN_PERCENT_WEEKLY -> max(0, minOf(ceil(start * 0.9.pow(weeks)).toInt(), start - weeks))
        }
    }

    /** The plan around [today], before any borrowing: today, tomorrow, and the next plan week. */
    fun outlook(s: UserSettings, today: LocalDate): PlanOutlook {
        val nextWeek = s.planStart.plusWeeks(weeksIntoPlan(s.planStart, today) + 1L)
        return PlanOutlook(
            baseToday = baseAllowance(s, today),
            baseTomorrow = baseAllowance(s, today.plusDays(1)),
            nextWeekStart = nextWeek,
            baseNextWeek = baseAllowance(s, nextWeek),
        )
    }

    /** The allowance in each of the first [weeks] weeks of a plan from [start], e.g. 14 → 13 → 12 → 11. */
    fun firstWeeks(start: Int, taper: TaperType, weeks: Int = 4): List<Int> {
        val day0 = LocalDate.of(2026, 1, 5) // any date: only the weeks since it matter
        val plan = UserSettings(startAllowance = start, taper = taper, planStart = day0)
        return (0 until weeks).map { w -> baseAllowance(plan, day0.plusWeeks(w.toLong())) }
    }

    /** The allowance on [date] after subtracting what was borrowed the day before. */
    fun allowance(s: UserSettings, date: LocalDate, smokedYesterday: Int): Int =
        max(0, baseAllowance(s, date) - debt(s, date.minusDays(1), smokedYesterday))

    /** How many cigarettes over the base allowance were smoked on [date]; 0 before the plan start. */
    fun debt(s: UserSettings, date: LocalDate, smokedThatDay: Int): Int {
        if (date.isBefore(s.planStart)) return 0
        return max(0, smokedThatDay - baseAllowance(s, date))
    }

    /** Minimum minutes between cigarettes for a given allowance. */
    fun intervalMinutes(allowance: Int, wakingMinutes: Int): Int =
        if (allowance <= 0) wakingMinutes else wakingMinutes / allowance
}

/** The plan's base allowances around a day; see [PlanMath.outlook]. */
data class PlanOutlook(val baseToday: Int, val baseTomorrow: Int, val nextWeekStart: LocalDate, val baseNextWeek: Int) {
    /** What the Today popover says tomorrow starts at when [over] are taken from [baseTomorrow]. */
    fun tomorrowAfter(over: Int): Int = (baseTomorrow - over).coerceAtLeast(0)
}
