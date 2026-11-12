package app.exhale.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Settings → Your data → Import from CSV: reads a file in [Export]'s format and adds what Exhale doesn't
 * have yet. Nothing already here is changed or removed, so importing the same file twice adds nothing
 * the second time.
 *
 * Reading ([decode], [parse]) is lenient about what a spreadsheet may have done to the file: an older
 * encoding than UTF-8, columns in any order and case, extra columns, a byte-order mark, LF or CRLF line
 * ends, `;` as the separator (Excel in many regions), and [Export]'s guard on formula-like cells. Each row
 * needs a `type` and a moment: the `timestamp`, or else `date` and `time` on the phone's clock. A row that
 * can't be read is left out and counted.
 */
object Import {
    /** Largest file read: decades of logs, small enough to read at once on any phone. */
    const val MAX_BYTES = 4 * 1024 * 1024

    /** One cigarette from the file. [trigger] is the label as written, or null. */
    data class CigaretteRow(val at: Instant, val trigger: String?)

    /** One craving wave from the file. */
    data class WaveRow(val start: Instant, val end: Instant, val outcome: UrgeOutcome, val trigger: String?)

    /** What a readable file holds: its rows, and how many rows couldn't be read. */
    data class Rows(val cigarettes: List<CigaretteRow>, val waves: List<WaveRow>, val unreadable: Int)

    /**
     * What importing would do, worked out against what's stored ([plan]): the [logs] and [urges] to add
     * (ids 0, oldest first), the trigger list to save (the same list when no trigger is new), the
     * [newTriggers] in it, and what is left out: [already] here, [later] than now, [unreadable].
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

        /** The first and last moment added; null when nothing is. */
        val span: Pair<Instant, Instant>? get() {
            val first = listOfNotNull(logs.firstOrNull()?.at, urges.firstOrNull()?.startedAt).minOrNull() ?: return null
            return first to listOfNotNull(logs.lastOrNull()?.at, urges.lastOrNull()?.startedAt).max()
        }

        /** New triggers whose names didn't survive the file's encoding ([garbled]). */
        val garbledTriggers: List<Trigger> get() = newTriggers.filter { garbled(it.label) }
    }

    /** What Settings asks before adding: "Add 412 cigarettes and 38 craving waves?" ([adds]), or that nothing is new. */
    data class Question(val title: String, val text: String, val adds: Boolean)

    /** A wave with no `minutes` lasted as long as the Urge Wave runs. */
    private val WAVE_LENGTH: Duration = Duration.ofSeconds(WAVE_SECONDS.toLong())

    /** Longest wave a row may give: anything longer is a mistake in the file. */
    private val WAVE_MAX: Duration = Duration.ofHours(24)

    /**
     * The file's text. UTF-8 (what Exhale writes) when it is valid UTF-8; otherwise the file was saved in an
     * older encoding (Excel's plain "CSV" on Windows saves in the region's code page), read as the Windows
     * code page for [locale]'s language ([legacyCharset]).
     */
    fun decode(bytes: ByteArray, locale: Locale = Locale.getDefault()): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, legacyCharset(locale))
    }

    /** The Windows code page a spreadsheet would have saved in for [locale]'s language: 1251, 1250, … else 1252. */
    fun legacyCharset(locale: Locale): Charset = Charset.forName(
        when (locale.language) {
            in setOf("ru", "uk", "be", "bg", "kk", "ky", "mk", "mn", "sr", "tg", "tt", "ba") -> "windows-1251"
            in setOf("pl", "cs", "sk", "hu", "sl", "hr", "ro", "sq", "bs") -> "windows-1250"
            "el" -> "windows-1253"
            "tr", "az" -> "windows-1254"
            else -> "windows-1252"
        },
    )

    /** Whether [label] looks broken by an encoding: a replacement character, or `??` where letters were. */
    fun garbled(label: String): Boolean = '�' in label || "??" in label

    /** The rows of [text], or null when it isn't a file Exhale can read (no `type` column, no moment). */
    fun parse(text: String, zone: ZoneId): Rows? {
        val body = text.removePrefix(Export.BOM)
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val sep = if (',' !in firstLine && ';' in firstLine) ';' else ','
        val table = table(body, sep).filterNot { row -> row.all { it.isBlank() } }
        val header = table.firstOrNull()?.map { it.trim().lowercase() } ?: return null
        fun col(name: String) = header.indexOf(name).takeIf { it >= 0 }
        val type = col(Export.TYPE) ?: return null
        val stamp = col(Export.TIMESTAMP)
        val date = col(Export.DATE)
        val time = col(Export.TIME)
        if (stamp == null && (date == null || time == null)) return null
        val trigger = col(Export.TRIGGER)
        val minutes = col(Export.MINUTES)
        val outcome = col(Export.OUTCOME)

        val cigarettes = mutableListOf<CigaretteRow>()
        val waves = mutableListOf<WaveRow>()
        var unreadable = 0
        for (row in table.drop(1)) {
            fun cell(i: Int?) = i?.let { row.getOrNull(it) }?.let(Export::unguard)?.trim().orEmpty()
            val at = moment(cell(stamp), cell(date), cell(time), zone)
            val label = cell(trigger).ifEmpty { null }
            when (cell(type).lowercase()) {
                Export.CIGARETTE -> if (at != null) cigarettes += CigaretteRow(at, label) else unreadable++
                Export.WAVE -> {
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
     * means the trigger already called that, else a new one ([Triggers.forName]), with its key from [newKey].
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
        val byLabel = mutableMapOf<String, Trigger?>()
        fun triggerFor(label: String?): Trigger? = label?.let {
            byLabel.getOrPut(it) { Triggers.forName(list, it, newKey())?.also { found -> list = found.list }?.trigger }
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
        val keys = triggers.mapTo(HashSet()) { it.key }
        return Plan(newLogs, newUrges, list, list.map { it.trigger }.filterNot { it.key in keys }, already, later, rows.unreadable)
    }

    /**
     * What Settings asks about [plan]: "Add 412 cigarettes and 38 craving waves?" with the days ("From 4
     * September to 12 November.", years once either isn't [today]'s), the new triggers, a note on garbled
     * names, and what stays or is left out; or "Nothing new in that file" when it adds nothing.
     */
    fun question(plan: Plan, today: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault()): Question {
        val leftOut = listOfNotNull(
            plan.later.takeIf { it > 0 }?.let { "$it later than now ${isAre(it)} left out." },
            plan.unreadable.takeIf { it > 0 }?.let { "${plural(it, "line")} couldn't be read and ${isAre(it)} left out." },
        )
        val counted = Export.counted(plan.counts)
            ?: return Question(
                "Nothing new in that file",
                (listOf(if (plan.already > 0) "What it has is already in Exhale." else "It has no cigarettes or craving waves to add.") + leftOut)
                    .joinToString(" "),
                adds = false,
            )
        val text = buildList {
            plan.span?.let { (from, to) -> add(days(from.atZone(zone).toLocalDate(), to.atZone(zone).toLocalDate(), today, locale)) }
            if (plan.newTriggers.isNotEmpty()) add("New for your triggers: ${plan.newTriggers.joinToString(", ") { it.label }}.")
            plan.garbledTriggers.firstOrNull()?.let {
                add("Some names look garbled, like “${it.label}”. In Excel, save the file as CSV UTF-8 and import it again.")
            }
            add(
                when (plan.already) {
                    0 -> "Nothing already here changes."
                    1 -> "1 already in Exhale stays as it is."
                    else -> "${plan.already} already in Exhale stay as they are."
                },
            )
            addAll(leftOut)
        }
        return Question("Add $counted?", text.joinToString(" "), adds = true)
    }

    /** What the toast says once [counts] were added; null for nothing. */
    fun addedNote(counts: Export.Counts): String? = Export.counted(counts)?.let { "Added $it" }

    private fun days(from: LocalDate, to: LocalDate, today: LocalDate, locale: Locale): String {
        val years = from.year != today.year || to.year != today.year
        val format = DateTimeFormatter.ofPattern(if (years) "d MMMM yyyy" else "d MMMM", locale)
        return if (from == to) "On ${format.format(from)}." else "From ${format.format(from)} to ${format.format(to)}."
    }

    private fun isAre(n: Int) = if (n == 1) "is" else "are"

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

    private fun waveOutcome(text: String): UrgeOutcome? =
        if (text.isEmpty()) UrgeOutcome.SURVIVED else Export.OUTCOMES.entries.firstOrNull { it.value == text.lowercase() }?.key

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
