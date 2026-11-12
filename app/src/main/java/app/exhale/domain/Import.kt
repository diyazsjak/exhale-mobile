package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Settings → Your data → Import from CSV: reads a file in [Export]'s format and adds what Exhale doesn't
 * have yet. Nothing already here is changed or removed, so importing the same file twice adds nothing
 * the second time.
 *
 * Reading ([parse]) is lenient about what a spreadsheet may have done to the file: columns are found by
 * their header names in any order and case, extra columns are ignored, a byte-order mark, LF or CRLF line
 * ends and `;` as the separator (Excel in many regions) are all fine, and [Export]'s leading `'` on
 * formula-like cells comes off again. Each row needs a `type` and a moment: the `timestamp`, or else
 * `date` and `time` on the phone's clock. A row that can't be read is left out and counted.
 */
object Import {
    /** One cigarette from the file. [trigger] is the label as written, or null. */
    data class CigaretteRow(val at: Instant, val trigger: String?)

    /** One craving wave from the file. */
    data class WaveRow(val start: Instant, val end: Instant, val outcome: UrgeOutcome, val trigger: String?)

    /** What a readable file holds: its rows, and how many rows couldn't be read. */
    data class Rows(val cigarettes: List<CigaretteRow>, val waves: List<WaveRow>, val unreadable: Int)

    /**
     * What importing would do, worked out against what's stored ([plan]): the [logs] and [urges] to add
     * (ids 0), the trigger list to save (the same list when no trigger is new), the [newTriggers] in it,
     * and what is left out: [already] here, [later] than now, [unreadable].
     */
    data class Plan(
        val logs: List<SmokeLog>,
        val urges: List<Urge>,
        val triggers: List<TriggerEntry>,
        val newTriggers: List<Trigger>,
        val already: Int,
        val later: Int,
        val unreadable: Int,
    ) {
        val adds: Boolean get() = logs.isNotEmpty() || urges.isNotEmpty()
        val counts: Export.Counts get() = Export.Counts(logs.size, urges.size)

        /** The first and last moment among what's added; null when nothing is. */
        val span: Pair<Instant, Instant>? get() {
            val all = logs.map { it.at } + urges.map { it.startedAt }
            return if (all.isEmpty()) null else all.min() to all.max()
        }
    }

    /** A wave with no `minutes` lasted as long as the Urge Wave runs. */
    private val WAVE_LENGTH: Duration = Duration.ofSeconds(WAVE_SECONDS.toLong())

    /** Longest wave a row may give: anything longer is a mistake in the file. */
    private val WAVE_MAX: Duration = Duration.ofHours(24)

    /** The rows of [text], or null when it isn't a file Exhale can read (no `type` column, no moment). */
    fun parse(text: String, zone: ZoneId): Rows? {
        val body = text.removePrefix("﻿")
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val sep = if (',' !in firstLine && ';' in firstLine) ';' else ','
        val table = table(body, sep).filterNot { row -> row.all { it.isBlank() } }
        val header = table.firstOrNull()?.map { it.trim().lowercase() } ?: return null
        fun col(name: String) = header.indexOf(name).takeIf { it >= 0 }
        val type = col("type") ?: return null
        val stamp = col("timestamp")
        val date = col("date")
        val time = col("time")
        if (stamp == null && (date == null || time == null)) return null
        val trigger = col("trigger")
        val minutes = col("minutes")
        val outcome = col("outcome")

        val cigarettes = mutableListOf<CigaretteRow>()
        val waves = mutableListOf<WaveRow>()
        var unreadable = 0
        for (row in table.drop(1)) {
            fun cell(i: Int?) = i?.let { row.getOrNull(it) }?.let(::unguard)?.trim().orEmpty()
            val at = moment(cell(stamp), cell(date), cell(time), zone)
            val label = cell(trigger).ifEmpty { null }
            when (cell(type).lowercase()) {
                "cigarette" -> if (at != null) cigarettes += CigaretteRow(at, label) else unreadable++
                "craving wave" -> {
                    val length = waveLength(cell(minutes))
                    val result = waveOutcome(cell(outcome))
                    if (at != null && length != null && result != null) waves += WaveRow(at, at + length, result, label) else unreadable++
                }
                else -> unreadable++
            }
        }
        return Rows(cigarettes, waves, unreadable)
    }

    /**
     * What importing [rows] adds to the stored [logs], [urges] and trigger list [triggers] at [now].
     * A row is already here when one of its kind starts in the same second (the file keeps whole
     * seconds), whether stored or earlier in the file. Rows after [now] are left out. A trigger label
     * means the trigger already called that (shown or hidden, which stays as it is); a label the list
     * doesn't have becomes a new trigger at the end of the shown ones ([Triggers.add]: a deleted
     * built-in comes back), with its key from [newKey].
     */
    fun plan(
        rows: Rows,
        logs: List<SmokeLog>,
        urges: List<Urge>,
        triggers: List<TriggerEntry>,
        now: Instant,
        newKey: () -> String = Triggers::newKey,
    ): Plan {
        var list = triggers
        val added = mutableListOf<Trigger>()
        val byLabel = mutableMapOf<String, Trigger?>()
        fun triggerFor(label: String?): Trigger? {
            val name = label?.let(Triggers::clean) ?: return null
            return byLabel.getOrPut(name) {
                Triggers.named(list, name)?.trigger ?: Triggers.add(list, name, newKey())?.let { a ->
                    list = a.list
                    a.trigger.also { added += it }
                }
            }
        }

        val logSeconds = logs.mapTo(HashSet()) { it.at.epochSecond }
        val urgeSeconds = urges.mapTo(HashSet()) { it.startedAt.epochSecond }
        var already = 0
        var later = 0
        val newLogs = mutableListOf<SmokeLog>()
        for (r in rows.cigarettes.sortedBy { it.at }) when {
            r.at > now -> later++
            !logSeconds.add(r.at.epochSecond) -> already++
            else -> newLogs += SmokeLog(0, r.at, triggerFor(r.trigger), fromUrge = false)
        }
        val newUrges = mutableListOf<Urge>()
        for (r in rows.waves.sortedBy { it.start }) when {
            r.start > now -> later++
            !urgeSeconds.add(r.start.epochSecond) -> already++
            // A wave can't run past now: one still running when the file was made ends where it was.
            else -> newUrges += Urge(0, r.start, minOf(r.end, now), r.outcome, triggerFor(r.trigger))
        }
        return Plan(newLogs, newUrges, list, added, already, later, rows.unreadable)
    }

    private fun moment(stamp: String, date: String, time: String, zone: ZoneId): Instant? {
        if (stamp.isNotEmpty()) {
            try {
                return OffsetDateTime.parse(stamp).toInstant()
            } catch (e: DateTimeParseException) {
                // Fall back to the date and time columns.
            }
        }
        return try {
            LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun waveLength(minutes: String): Duration? {
        if (minutes.isEmpty()) return WAVE_LENGTH
        val m = minutes.toLongOrNull() ?: return null
        return Duration.ofMinutes(m).takeIf { !it.isNegative && it <= WAVE_MAX }
    }

    private fun waveOutcome(text: String): UrgeOutcome? = when (text.lowercase()) {
        "", "rode it out" -> UrgeOutcome.SURVIVED
        "smoked" -> UrgeOutcome.SMOKED
        else -> null
    }

    /** Takes off the `'` [Export.cell] puts before a cell that starts like a formula. */
    private fun unguard(cell: String): String =
        if (cell.length > 1 && cell[0] == '\'' && cell[1] in "=+-@\t\r") cell.substring(1) else cell

    /** RFC 4180 cells: quoted cells may hold [sep], quotes (doubled) and line breaks. */
    internal fun table(text: String, sep: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { row += cell.toString(); cell.clear() }
        fun endRow() { endCell(); rows += row; row = mutableListOf() }
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (text.getOrNull(i + 1) == '"') { cell.append('"'); i++ } else quoted = false
                } else {
                    cell.append(ch)
                }
            } else when (ch) {
                '"' -> quoted = true
                sep -> endCell()
                '\r' -> { endRow(); if (text.getOrNull(i + 1) == '\n') i++ }
                '\n' -> endRow()
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
