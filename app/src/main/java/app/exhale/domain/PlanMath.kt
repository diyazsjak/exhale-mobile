package app.exhale.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/**
 * Pure rules for the "Regulate" mode. No Android types, fully unit-tested.
 *
 * - Allowance tapers weekly from [UserSettings.startAllowance] according to [TaperType].
 * - Smoking over the allowance borrows from the next day (debt), never more than one day back.
 * - The interval spreads the day's allowance over waking minutes.
 */
object PlanMath {

    fun weeksIntoPlan(start: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(start, date)
        return if (days < 0) 0 else (days / 7).toInt()
    }

    fun baseAllowance(s: UserSettings, date: LocalDate): Int {
        val weeks = weeksIntoPlan(s.planStart, date)
        val start = s.startAllowance.coerceAtLeast(0)
        return when (s.taper) {
            TaperType.HOLD -> start
            TaperType.MINUS_ONE_WEEKLY -> max(0, start - weeks)
            TaperType.TEN_PERCENT_WEEKLY -> ceil(start * 0.9.pow(weeks)).toInt().let {
                // Always move at least one per week so the plan never stalls on small numbers.
                max(0, minOf(it, start - weeks))
            }
        }
    }

    /** The allowance in each of the first [weeks] weeks of a plan from [start], e.g. 14 → 13 → 12 → 11. */
    fun firstWeeks(start: Int, taper: TaperType, weeks: Int = 4): List<Int> {
        val day0 = LocalDate.of(2026, 1, 5)
        val plan = UserSettings(startAllowance = start, taper = taper, planStart = day0)
        return (0 until weeks).map { w -> baseAllowance(plan, day0.plusWeeks(w.toLong())) }
    }

    /** Today's allowance after subtracting what was borrowed yesterday. */
    fun allowance(s: UserSettings, date: LocalDate, smokedYesterday: Int): Int {
        val debt = debt(s, date.minusDays(1), smokedYesterday)
        return max(0, baseAllowance(s, date) - debt)
    }

    /** How many cigarettes over the base allowance were smoked on [date]. */
    fun debt(s: UserSettings, date: LocalDate, smokedThatDay: Int): Int {
        if (date.isBefore(s.planStart)) return 0
        return max(0, smokedThatDay - baseAllowance(s, date))
    }

    /** Minimum minutes between cigarettes for a given allowance. */
    fun intervalMinutes(allowance: Int, wakingMinutes: Int): Int =
        if (allowance <= 0) wakingMinutes else wakingMinutes / allowance
}
