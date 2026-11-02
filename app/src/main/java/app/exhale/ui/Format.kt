package app.exhale.ui

import java.text.NumberFormat
import java.time.Duration
import java.util.Currency
import java.util.Locale

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

    /** "00:38:12". */
    fun clock(d: Duration?): String {
        if (d == null) return "00:00:00"
        val s = d.seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    fun mmss(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

    fun money(minor: Long, currencyCode: String, decimals: Boolean = false): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        runCatching { nf.currency = Currency.getInstance(currencyCode) }
        val digits = if (decimals) nf.currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2 else 0
        nf.maximumFractionDigits = digits
        nf.minimumFractionDigits = digits
        return nf.format(minor / 100.0)
    }
}
