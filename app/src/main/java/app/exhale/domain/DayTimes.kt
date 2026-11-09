package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One thing on a day's list ([DayTimes.entries]), at the moment it happened. */
sealed interface DayEntry {
    val at: Instant

    /**
     * A cigarette: the [number]th of the day, [over] when that is past the day's allowance, and the
     * time since the cigarette before it ([gapBefore]; null for the first one ever).
     */
    data class Cigarette(val log: SmokeLog, val number: Int, val over: Boolean, val gapBefore: Duration?) : DayEntry {
        override val at: Instant get() = log.at
    }

    /** A craving wave ridden out, placed by when it started. */
    data class Wave(val urge: Urge) : DayEntry {
        override val at: Instant get() = urge.startedAt
        val length: Duration get() = Duration.between(urge.startedAt, urge.endedAt)
    }
}

/**
 * One day, cigarette by cigarette: what Rhythm lists on a picked day and the Ember row draws for
 * today. Built by [DayTimes.of].
 */
data class DayTimes(
    val day: LocalDate,
    /** Cigarettes and waves ridden out on [day], in time order. */
    val entries: List<DayEntry>,
    /** The last cigarette before [day], which the first gap counts from; null when there is none. */
    val previous: SmokeLog?,
    /** The day's allowance; null before the plan start, when nothing is "over". */
    val allowance: Int?,
    /** For today: the running gap ([Gap.ongoing]), from the last cigarette to now. Null on other days or before any log. */
    val running: Gap?,
    /** Where the Ember row's line starts, in minutes after midnight; see [lineStart]. It ends at midnight. */
    val lineStartMinute: Int,
) {
    val cigarettes: List<DayEntry.Cigarette> get() = entries.filterIsInstance<DayEntry.Cigarette>()
    val count: Int get() = cigarettes.size

    /** Nothing to list: no cigarette or wave on the day, and (today) nothing logged before it either. */
    val isEmpty: Boolean get() = entries.isEmpty() && running == null

    /**
     * The longest stretch in the day: between two of its cigarettes, or, today, from the last one to
     * now. The night before the first one doesn't count. Null with nothing to measure.
     */
    val longestGap: Gap?
        get() {
            val times = cigarettes.map { it.at }
            val gaps = times.zipWithNext { a, b -> Gap(a, b, ongoing = false) } + listOfNotNull(running?.takeIf { times.isNotEmpty() })
            return gaps.maxByOrNull { it.length }
        }

    companion object {
        /** The Ember row's line starts at 6 am, or on the hour of an earlier first cigarette. */
        const val LINE_START_MINUTE = 6 * 60

        /**
         * [day] from [logs] (oldest first, as stored) and [urges] (any order). Only waves ridden out
         * show. [now] makes today's running gap and leaves out anything after it. Runs every second
         * for today, so it walks back from the newest log instead of going through the whole history.
         */
        fun of(
            logs: List<SmokeLog>,
            urges: List<Urge>,
            day: LocalDate,
            zone: ZoneId,
            allowance: Int?,
            now: Instant,
        ): DayTimes {
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant()
            var i = logs.lastIndex
            while (i >= 0 && logs[i].at > now) i--
            val last = logs.getOrNull(i)
            while (i >= 0 && logs[i].at >= end) i--
            val dayEnd = i
            while (i >= 0 && logs[i].at >= start) i--
            val onDay = logs.subList(i + 1, dayEnd + 1)
            val previous = logs.getOrNull(i)
            var before = previous?.at
            val cigarettes = onDay.mapIndexed { i, log ->
                val number = i + 1
                DayEntry.Cigarette(log, number, allowance != null && number > allowance, before?.let { Duration.between(it, log.at) })
                    .also { before = log.at }
            }
            val waves = urges
                .filter { it.outcome == UrgeOutcome.SURVIVED && it.startedAt >= start && it.startedAt < end && it.startedAt <= now }
                .map { DayEntry.Wave(it) }
            val isToday = now >= start && now < end
            return DayTimes(
                day = day,
                entries = (cigarettes + waves).sortedBy { it.at },
                previous = previous,
                allowance = allowance,
                running = if (isToday && last != null) Gap(last.at, now, ongoing = true) else null,
                lineStartMinute = lineStart(onDay.firstOrNull()?.at?.atZone(zone)?.toLocalTime()),
            )
        }

        /** Minutes after midnight of [at] on its own day. */
        fun minuteOf(at: Instant, zone: ZoneId): Int = at.atZone(zone).let { it.hour * 60 + it.minute }

        /** [LINE_START_MINUTE], or the hour of [first] when that is earlier. */
        fun lineStart(first: LocalTime?): Int =
            if (first == null) LINE_START_MINUTE else minOf(LINE_START_MINUTE, first.hour * 60)
    }
}

/** Rules for changing a logged cigarette's time (the edit dialog on Rhythm's day list). */
object LogEdit {
    /** [minuteOfDay] with its hour moved by [delta], round the clock; the minutes stay. */
    fun stepHour(minuteOfDay: Int, delta: Int): Int = Math.floorMod(minuteOfDay / 60 + delta, 24) * 60 + minuteOfDay % 60

    /** [minuteOfDay] with its minutes moved by [delta], round the hour; the hour stays. */
    fun stepMinute(minuteOfDay: Int, delta: Int): Int = minuteOfDay / 60 * 60 + Math.floorMod(minuteOfDay % 60 + delta, 60)

    /**
     * The moment a log at [original] moves to when set to [minuteOfDay]: on the same day, at the
     * start of that minute. Unchanged minutes keep [original] to the second. Null when that moment
     * is after [now]: a log can't move into the future.
     */
    fun moved(original: Instant, minuteOfDay: Int, zone: ZoneId, now: Instant): Instant? {
        if (DayTimes.minuteOf(original, zone) == minuteOfDay) return original
        val day = original.atZone(zone).toLocalDate()
        val at = day.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toInstant()
        return at.takeIf { it <= now }
    }
}
