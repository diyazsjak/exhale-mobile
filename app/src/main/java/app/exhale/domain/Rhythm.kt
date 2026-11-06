package app.exhale.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Everything Rhythm shows for one view: the last [Rhythm.WINDOW] days under a day filter, or one
 * picked day. Built by [Rhythm.view].
 */
class RhythmView(
    /** The days shown. */
    val period: Period,
    /** Per hour, the average a day over [period] (a picked day's own counts); see [Stats.hourly]. */
    val hours: FloatArray,
    /** The same for the [Rhythm.WINDOW] days before, once they cover [Rhythm.MIN_DAYS]; never on a picked day. */
    val before: FloatArray?,
    /** Days of [period] that count; see [Stats.coveredDays]. */
    val covered: Int,
    /** Tracking began inside [period], so its average is "since you started". */
    val sinceStart: Boolean,
    val triggers: TriggerSummary,
    /** The busiest hour first; see [Stats.peaks]. Empty with nothing logged. */
    val peaks: List<Int>,
    /** Craving waves ridden out in [period]. */
    val wavesRidden: Int,
    /** Days since tracking began, today included; 0 before any data. */
    val trackedDays: Long,
    /** Days until the [Rhythm.WINDOW] days before cover [Rhythm.MIN_DAYS], so hours can be compared. */
    val daysToCompare: Long,
    /** The picked day's count and allowance; null for the last [Rhythm.WINDOW] days. */
    val day: DayTally?,
    /** The logs in [period], for [topTrigger]. */
    private val logs: List<SmokeLog>,
    private val zone: ZoneId,
) {
    /** The average a day over [period]: the sum of [hours]. */
    val average: Float get() = hours.sum()

    /** The plan card shows on the last 30 days with a week covered and something logged. */
    val showPlan: Boolean get() = day == null && covered >= Rhythm.MIN_DAYS && peaks.isNotEmpty()

    /** The trigger most logs at [hour] were tagged with (see [Stats.topTrigger]); not on a picked day. */
    fun topTrigger(hour: Int): TopTrigger? =
        if (day != null) null else Stats.topTrigger(logs.filter { it.at.atZone(zone).hour == hour })
}

object Rhythm {
    /** Days Rhythm looks back over, today included, and the length of the stretch it compares with. */
    const val WINDOW = 30

    /** Days of history (in the period and the one before) needed before plans and comparisons show. */
    const val MIN_DAYS = 7

    /** Rhythm for the last [WINDOW] days under [filter], or for one picked [day]. */
    fun view(
        s: UserSettings,
        logs: List<SmokeLog>,
        urges: List<Urge>,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        dataStart: LocalDate?,
        day: LocalDate?,
        filter: DayFilter,
    ): RhythmView {
        val period = day?.let { Period.day(it) } ?: Period.lastDays(today, WINDOW, 0, filter)
        val before = Period.lastDays(today, WINDOW, WINDOW, filter)
        val compare = day == null && Stats.coveredDays(before, now, zone, dataStart) >= MIN_DAYS
        val inPeriod = logs.filter { period.contains(it.at, zone) }
        val hours = Stats.hourly(logs, period, now, zone, dataStart)
        return RhythmView(
            period = period,
            hours = hours,
            before = if (compare) Stats.hourly(logs, before, now, zone, dataStart) else null,
            covered = Stats.coveredDays(period, now, zone, dataStart),
            sinceStart = dataStart != null && dataStart.isAfter(period.first),
            triggers = Stats.triggers(inPeriod),
            peaks = Stats.peaks(hours),
            wavesRidden = urges.count { period.contains(it.startedAt, zone) && it.outcome == UrgeOutcome.SURVIVED },
            trackedDays = dataStart?.let { ChronoUnit.DAYS.between(it, today) + 1 } ?: 0,
            // The days before cover MIN_DAYS once tracking began WINDOW + MIN_DAYS - 1 days ago.
            daysToCompare = dataStart?.let { ChronoUnit.DAYS.between(today, it.plusDays(WINDOW + MIN_DAYS - 1L)) } ?: 0,
            // Only the day and the one before (for borrowing) matter: count those, not the whole history.
            day = day?.let { d -> History.tally(s, listOf(d, d.minusDays(1)).associateWith { Stats.countOn(logs, it, zone) }, d) },
            logs = inPeriod,
            zone = zone,
        )
    }
}
