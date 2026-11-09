package app.exhale.ui

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

object Fmt {
    /** "2h 41m", "14m", "3d 2h". */
    fun gap(d: Duration?): String {
        if (d == null) return "—"
        val mins = d.toMinutes().coerceAtLeast(0)
        val days = mins / 1440
        val h = (mins % 1440) / 60
        val m = mins % 60
        return when {
            days > 0 -> "${days}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    /** How long a wave lasted, at least a minute: "4 min". */
    fun waveLength(d: Duration): String = "${d.toMinutes().coerceAtLeast(1)} min"

    /**
     * A wait in round terms, for things hours to years away: "9h 19m" under two days, then
     * "13 days", "11 months", "1 year", "5 years".
     */
    fun roughly(d: Duration): String {
        val days = d.toDays()
        return when {
            days < 2 -> gap(d)
            days < 60 -> "$days days"
            days < 335 -> "${days / 30} months"
            else -> (days / 365.0).roundToInt().coerceAtLeast(1).let { if (it == 1) "1 year" else "$it years" }
        }
    }

    /** "00:38:12". */
    fun clock(d: Duration?): String {
        if (d == null) return "00:00:00"
        val s = d.seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /** A time of day following the phone's 12/24-hour setting: "13:05" or "1:05 PM". */
    fun time(at: Instant, zone: ZoneId, h24: Boolean): String = at.atZone(zone).format(clockFormat(h24))

    // Built once per locale: times are formatted for every row, every second on Ember.
    private var clockFormats: Pair<Locale, Array<DateTimeFormatter>>? = null

    private fun clockFormat(h24: Boolean): DateTimeFormatter {
        val locale = Locale.getDefault()
        val cached = clockFormats?.takeIf { it.first == locale }?.second
            ?: arrayOf(DateTimeFormatter.ofPattern("h:mm a", locale), DateTimeFormatter.ofPattern("HH:mm", locale))
                .also { clockFormats = locale to it }
        return cached[if (h24) 1 else 0]
    }

    /** "Today", "Yesterday" (with [yesterday]), a weekday name within the last week, else "Sun 12 Oct". */
    fun dayName(d: LocalDate, today: LocalDate, yesterday: Boolean = false): String = when {
        d == today -> "Today"
        yesterday && d == today.minusDays(1) -> "Yesterday"
        !d.isBefore(today.minusDays(6)) -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }

    /** "4 September": a date within the year, e.g. when the plan started. */
    fun dayMonth(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))

    fun mmss(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

    /** Money as "12,345 KZT" / "62 $" / "10.00 $": the number first, then the currency symbol or code. */
    fun money(minor: Long, currencyCode: String, decimals: Boolean = false): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val digits = if (decimals) currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2 else 0
        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = digits
            minimumFractionDigits = digits
        }
        val symbol = currency?.getSymbol(Locale.getDefault()) ?: currencyCode
        return nf.format(if (decimals) minor / 100.0 else (minor / 100).toDouble()) + "\u00A0" + symbol
    }
}
