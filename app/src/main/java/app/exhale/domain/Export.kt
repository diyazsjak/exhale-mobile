package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Settings → Your data → Export as CSV: every cigarette and craving wave as one CSV file, for a
 * spreadsheet ([Import] reads it back). One row per event, oldest first, with the columns in [HEADER]:
 * - `type`: [CIGARETTE] or [WAVE].
 * - `date`, `time`: on the phone's clock (`2026-11-12`, `08:14`, always 24-hour, so spreadsheets read it).
 * - `trigger`: its label as the user sees it, or empty.
 * - `minutes`, `outcome`: craving waves only: how long it lasted, rounded to the minute, and the
 *   [OUTCOMES] word.
 * - `timestamp`: the exact moment with its UTC offset (ISO 8601, to the second), so nothing is lost.
 *
 * RFC 4180: comma-separated, CRLF line ends, a cell with a comma, quote or line break in quotes (quotes
 * doubled). It starts with a UTF-8 byte-order mark ([BOM]) so Excel reads the user's own trigger names in
 * any script. A cell that starts like a formula (after any `'`s) gets a leading `'` ([guard]), so a
 * spreadsheet shows it as text instead of running it; [unguard] takes exactly that off.
 */
object Export {
    const val TYPE = "type"
    const val DATE = "date"
    const val TIME = "time"
    const val TRIGGER = "trigger"
    const val MINUTES = "minutes"
    const val OUTCOME = "outcome"
    const val TIMESTAMP = "timestamp"
    val HEADER = listOf(TYPE, DATE, TIME, TRIGGER, MINUTES, OUTCOME, TIMESTAMP)

    const val CIGARETTE = "cigarette"
    const val WAVE = "craving wave"
    val OUTCOMES: Map<UrgeOutcome, String> = mapOf(UrgeOutcome.SURVIVED to "rode it out", UrgeOutcome.SMOKED to "smoked")

    const val MIME = "text/csv"
    internal const val BOM = "﻿"
    private const val EOL = "\r\n"

    /** First characters that make a spreadsheet read a cell as a formula. */
    private const val FORMULA = "=+-@\t\r"

    /** What a file held: how many cigarettes and craving waves. */
    data class Counts(val cigarettes: Int, val waves: Int)

    /** The file name the save dialog suggests: `exhale-2026-11-12.csv`. */
    fun fileName(today: LocalDate): String = "exhale-$today.csv"

    /** [logs] and [urges] (in any order) as the CSV file's text. */
    fun csv(logs: List<SmokeLog>, urges: List<Urge>, zone: ZoneId): String {
        val rows = logs.map { it.at to cigaretteRow(it, zone) } + urges.map { it.startedAt to waveRow(it, zone) }
        return buildString {
            append(BOM)
            append(line(HEADER))
            // Stable: at the same moment cigarettes stay before waves, each in stored order.
            rows.sortedBy { it.first }.forEach { append(line(it.second)) }
        }
    }

    /** What the toast says once the file is saved. */
    fun savedNote(counts: Counts): String =
        counted(counts)?.let { "Saved $it" } ?: "Saved. Nothing logged yet, so it has only the headings."

    /** "412 cigarettes and 38 craving waves", leaving out a kind there are none of; null for none at all. */
    fun counted(counts: Counts): String? = listOfNotNull(
        counts.cigarettes.takeIf { it > 0 }?.let { plural(it, CIGARETTE) },
        counts.waves.takeIf { it > 0 }?.let { plural(it, WAVE) },
    ).joinToString(" and ").ifEmpty { null }

    private fun cigaretteRow(log: SmokeLog, zone: ZoneId): List<String> {
        val at = log.at.atZone(zone)
        return listOf(CIGARETTE, at.toLocalDate().toString(), CLOCK.format(at), log.trigger?.label.orEmpty(), "", "", stamp(log.at, zone))
    }

    private fun waveRow(u: Urge, zone: ZoneId): List<String> {
        val at = u.startedAt.atZone(zone)
        val seconds = Duration.between(u.startedAt, u.endedAt).seconds.coerceAtLeast(0)
        return listOf(
            WAVE, at.toLocalDate().toString(), CLOCK.format(at), u.trigger?.label.orEmpty(),
            ((seconds + 30) / 60).toString(), OUTCOMES.getValue(u.outcome), stamp(u.startedAt, zone),
        )
    }

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun stamp(at: Instant, zone: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(at.truncatedTo(ChronoUnit.SECONDS).atZone(zone))

    private fun line(cells: List<String>): String = cells.joinToString(",", postfix = EOL) { cell(it) }

    /** One cell, kept as text ([guard]) and quoted when it has to be. */
    internal fun cell(text: String): String {
        val safe = guard(text)
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** [text] with a leading `'` when it [looksLikeFormula]. */
    internal fun guard(text: String): String = if (looksLikeFormula(text)) "'$text" else text

    /**
     * The inverse of [guard]: a `'` before something that [looksLikeFormula] comes off; anything else stays.
     * Files from 0.17.0, which guarded only formula characters, read back the same way.
     */
    internal fun unguard(cell: String): String =
        if (cell.startsWith('\'') && looksLikeFormula(cell.substring(1))) cell.substring(1) else cell

    /**
     * Whether a spreadsheet would run [text], or it is one already guarded: a formula character after any
     * leading `'`s. So `'=x` is guarded again (`''=x`) and comes back whole, while `'hi` is left as it is.
     */
    private fun looksLikeFormula(text: String): Boolean = text.trimStart('\'').firstOrNull()?.let { it in FORMULA } == true
}

/** "1 cigarette", "3 craving waves". */
internal fun plural(n: Int, word: String): String = if (n == 1) "1 $word" else "$n ${word}s"
