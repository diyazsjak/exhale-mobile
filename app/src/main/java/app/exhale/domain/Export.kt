package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Settings → Your data → Export as CSV: every cigarette and craving wave as one CSV file, for a
 * spreadsheet. One row per event, oldest first, with the columns in [HEADER]:
 * - `type`: "cigarette" or "craving wave".
 * - `date`, `time`: on the phone's clock (`2026-11-12`, `08:14`, always 24-hour, so spreadsheets read it).
 * - `trigger`: its label as the user sees it, or empty.
 * - `minutes`, `outcome`: craving waves only: how long it lasted, rounded to the minute, and "rode it
 *   out" or "smoked".
 * - `timestamp`: the exact moment with its UTC offset (ISO 8601, to the second), so nothing is lost.
 *
 * RFC 4180: comma-separated, CRLF line ends, a cell with a comma, quote or line break in quotes (quotes
 * doubled). It starts with a UTF-8 byte-order mark so Excel reads the user's own trigger names in any
 * script. A cell that starts like a formula (`=`, `+`, `-`, `@`, tab, CR) gets a leading `'` so a
 * spreadsheet shows it as text instead of running it.
 */
object Export {
    val HEADER = listOf("type", "date", "time", "trigger", "minutes", "outcome", "timestamp")

    const val MIME = "text/csv"
    private const val BOM = "﻿"
    private const val EOL = "\r\n"

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
    fun savedNote(counts: Counts): String {
        val parts = listOfNotNull(
            counts.cigarettes.takeIf { it > 0 }?.let { plural(it, "cigarette") },
            counts.waves.takeIf { it > 0 }?.let { plural(it, "craving wave") },
        )
        return if (parts.isEmpty()) "Saved. Nothing logged yet, so it has only the headings." else "Saved " + parts.joinToString(" and ")
    }

    private fun cigaretteRow(log: SmokeLog, zone: ZoneId): List<String> {
        val at = log.at.atZone(zone)
        return listOf("cigarette", at.toLocalDate().toString(), TIME.format(at), log.trigger?.label.orEmpty(), "", "", stamp(log.at, zone))
    }

    private fun waveRow(u: Urge, zone: ZoneId): List<String> {
        val at = u.startedAt.atZone(zone)
        val seconds = Duration.between(u.startedAt, u.endedAt).seconds.coerceAtLeast(0)
        val outcome = when (u.outcome) {
            UrgeOutcome.SURVIVED -> "rode it out"
            UrgeOutcome.SMOKED -> "smoked"
        }
        return listOf(
            "craving wave", at.toLocalDate().toString(), TIME.format(at), u.trigger?.label.orEmpty(),
            ((seconds + 30) / 60).toString(), outcome, stamp(u.startedAt, zone),
        )
    }

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun stamp(at: Instant, zone: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(at.truncatedTo(ChronoUnit.SECONDS).atZone(zone))

    private fun line(cells: List<String>): String = cells.joinToString(",", postfix = EOL) { cell(it) }

    /** One cell, kept as text and quoted when it has to be. */
    internal fun cell(text: String): String {
        val safe = if (text.isNotEmpty() && text[0] in "=+-@\t\r") "'$text" else text
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}
