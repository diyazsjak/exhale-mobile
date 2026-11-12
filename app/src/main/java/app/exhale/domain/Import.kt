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

    /** What reading a file gave ([read]). */
    sealed interface Read {
        data class Ok(val rows: Rows) : Read
        /** Over [MAX_BYTES]. */
        data object TooBig : Read
        /** Not a file Exhale can read: no `type` column, or no moment. */
        data object NotExhale : Read
    }

    /**
     * What importing would do, worked out against what's stored ([plan]): the [logs] and [urges] to add
     * (ids 0, oldest first), the trigger list to save (the same list when no trigger is new), the
     * [newTriggers] in it, what is left out ([already] here, [later] than now, [unreadable]), and how many
     * rows were [repeated] in the file itself (each added once).
     */
    data class Plan(
        val logs: List<SmokeLog>,
        val urges: List<Urge>,
        val triggers: List<TriggerEntry>,
        val newTriggers: List<Trigger>,
        val already: Int,
        val later: Int,
        val unreadable: Int,
        val repeated: Int = 0,
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
     * Reads a file's [bytes]: [Read.TooBig] over [MAX_BYTES], else [decode]d and [parse]d.
     */
    fun read(bytes: ByteArray, zone: ZoneId, locale: Locale = Locale.getDefault()): Read = when {
        bytes.size > MAX_BYTES -> Read.TooBig
        else -> parse(decode(bytes, locale), zone, locale)?.let { Read.Ok(it) } ?: Read.NotExhale
    }

    /**
     * The file's text. UTF-8 (what Exhale writes) when it is valid UTF-8; otherwise the file was saved in an
     * older encoding (Excel's plain "CSV" on Windows saves in the region's code page). Then each Windows code
     * page is tried, [locale]'s first ([legacyCharset]), and the one whose words read most like words
     * ([oddWords]) wins: a Cyrillic file on an English phone still reads as Cyrillic.
     */
    fun decode(bytes: ByteArray, locale: Locale = Locale.getDefault()): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        val preferred = legacyCharset(locale)
        (listOf(preferred) + LEGACY.map(Charset::forName).filter { it != preferred })
            .map { String(bytes, it) }
            .minBy(::oddWords) // the first of equals: the phone's own code page on a tie
    }

    private val LEGACY = listOf("windows-1252", "windows-1251", "windows-1250", "windows-1253", "windows-1254")

    /**
     * How many words in [text] look like the wrong code page: letters from two scripts in one word ("Cafй"),
     * or three accented Latin-1 letters in a row ("Àâòîáóñ", Cyrillic read as 1252).
     */
    internal fun oddWords(text: String): Int = WORD.findAll(text).count { m ->
        val w = m.value
        if (w.all { it.code < 0x80 }) return@count false // plain ASCII: nothing to get wrong
        val scripts = w.codePoints().toArray().map { Character.UnicodeScript.of(it) }.filter { it != Character.UnicodeScript.COMMON && it != Character.UnicodeScript.INHERITED }.toSet()
        scripts.size > 1 || LATIN1_RUN.containsMatchIn(w)
    }

    private val WORD = Regex("\\p{L}+")
    private val LATIN1_RUN = Regex("[\\u00C0-\\u00FF]{3,}")

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

    /**
     * Whether [label] looks broken by an encoding: a replacement character, mostly `?` where letters were
     * (a code page that couldn't hold them), or words from the wrong code page ([oddWords]).
     */
    fun garbled(label: String): Boolean {
        val marks = label.count { it == '?' }
        return '\uFFFD' in label || (marks >= 2 && marks * 2 > label.count { !it.isWhitespace() }) || oddWords(label) > 0
    }

    /**
     * The rows of [text], or null when it isn't a file Exhale can read (no `type` column, no moment).
     * [locale] settles the order of a slashed date a spreadsheet wrote (`11/12/2026`) when neither part is past 12.
     */
    fun parse(text: String, zone: ZoneId, locale: Locale = Locale.getDefault()): Rows? {
        val body = text.removePrefix(Export.BOM)
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
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
            val at = moment(cell(stamp), cell(date), cell(time), zone, locale)
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
     * A row is already here when one of its kind is stored starting in the same second (the file keeps
     * whole seconds); one the file has twice is added once ([Plan.repeated]). Rows after [now] are left out. A trigger label
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

        val storedLogs = logs.mapTo(HashSet()) { it.at.epochSecond }
        val storedUrges = urges.mapTo(HashSet()) { it.startedAt.epochSecond }
        val fileLogs = HashSet<Long>()
        val fileUrges = HashSet<Long>()
        var already = 0
        var repeated = 0
        var later = 0
        val newLogs = mutableListOf<SmokeLog>()
        for (r in rows.cigarettes.sortedBy { it.at }) when {
            r.at > now -> later++
            r.at.epochSecond in storedLogs -> already++
            !fileLogs.add(r.at.epochSecond) -> repeated++
            else -> newLogs += SmokeLog(0, r.at, triggerFor(r.trigger), fromUrge = false)
        }
        val newUrges = mutableListOf<Urge>()
        for (r in rows.waves.sortedBy { it.start }) when {
            r.start > now -> later++
            r.start.epochSecond in storedUrges -> already++
            !fileUrges.add(r.start.epochSecond) -> repeated++
            // A wave can't run past now: one still running when the file was made ends where it was.
            else -> newUrges += Urge(0, r.start, minOf(r.end, now), r.outcome, triggerFor(r.trigger))
        }
        val keys = triggers.mapTo(HashSet()) { it.key }
        val added = list.map { it.trigger }.filterNot { it.key in keys }
        return Plan(newLogs, newUrges, list, added, already, later, rows.unreadable, repeated)
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
            if (plan.repeated > 0) add("${plan.repeated} repeated in the file ${isAre(plan.repeated)} added once.")
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

    private fun moment(stamp: String, date: String, time: String, zone: ZoneId, locale: Locale): Instant? {
        if (stamp.isNotEmpty()) {
            try {
                return OffsetDateTime.parse(stamp).toInstant()
            } catch (e: DateTimeParseException) {
                // Fall back to the date and time columns.
            }
        }
        val d = day(date, locale) ?: return null
        val t = clock(time) ?: return null
        return d.atTime(t).atZone(zone).toInstant()
    }

    /**
     * A date as Exhale writes it (`2026-11-12`) or as a spreadsheet may have rewritten it: `12.11.2026`
     * (day first), or `12/11/2026`, which is day first except when the second part is past 12, or when
     * neither part is and [locale] is American (`11/12/2026` is 12 November there).
     */
    internal fun day(text: String, locale: Locale): LocalDate? {
        try {
            return LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            // Not ISO: try what a spreadsheet writes.
        }
        val dotted = DOTTED.matchEntire(text)
        val match = dotted ?: SLASHED.matchEntire(text) ?: return null
        val (a, b, y) = match.groupValues.drop(1).map { it.toInt() }
        val monthFirst = dotted == null && (b > 12 || (a <= 12 && locale.country == "US"))
        return try {
            if (monthFirst) LocalDate.of(y, a, b) else LocalDate.of(y, b, a)
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    private val DOTTED = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})")
    private val SLASHED = Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})")

    /** A time as Exhale writes it (`08:14`) or as a spreadsheet may show it: `8:14`, `08:14:03`, `8:14 PM`. */
    internal fun clock(text: String): LocalTime? = CLOCKS.firstNotNullOfOrNull { f ->
        try {
            LocalTime.parse(text.uppercase(Locale.ROOT), f)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private val CLOCKS = listOf("H:mm[:ss]", "h:mm[:ss] a", "h:mm[:ss]a").map { DateTimeFormatter.ofPattern(it, Locale.US) }

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
        var start = true // a quote opens a quoted cell only at its start; anywhere else it is just a quote
        var i = 0
        fun endCell() { row += cell.toString(); cell.clear(); start = true }
        fun endRow() { endCell(); rows += row; row = mutableListOf() }
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (text.getOrNull(i + 1) == '"') { cell.append('"'); i++ } else quoted = false
                } else {
                    cell.append(ch)
                }
            } else when {
                ch == '"' && start -> { quoted = true; start = false }
                ch == sep -> endCell()
                ch == '\r' -> { endRow(); if (text.getOrNull(i + 1) == '\n') i++ }
                ch == '\n' -> endRow()
                else -> { cell.append(ch); start = false }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
