package app.exhale.widget

import app.exhale.domain.WidgetKind
import app.exhale.domain.WidgetModel
import app.exhale.ui.Fmt
import java.time.Instant
import java.time.ZoneId

/**
 * The widget's words. A home screen is seen by others, so nothing here says "cigarette" or
 * "smoke": just the count and the times. Over the allowance it is "borrowed from tomorrow".
 */
object WidgetCopy {
    enum class Tone { SOFT, STRONG, OXY, FLARE }

    /** One line: [lead], then [strong] (a time, in bold `ink`) and a quieter [tail]. */
    data class Line(val lead: String, val tone: Tone = Tone.SOFT, val strong: String = "", val tail: String = "") {
        val text: String get() = listOf(lead, strong).filter { it.isNotEmpty() }.joinToString(" ") + tail
    }

    /** The 4×1's first line: "4 of 10 today". */
    fun title(m: WidgetModel): String = if (m.kind == WidgetKind.SETUP) "Exhale" else "${m.count} of ${m.allowance} today"

    /** The line under the ring (2×2) or under the title ([wide], the 4×1). */
    fun line(m: WidgetModel, wide: Boolean, zone: ZoneId, h24: Boolean): Line {
        fun t(at: Instant?) = at?.let { Fmt.time(it, zone, h24) } ?: ""
        val borrowed = wide && m.borrowingFromTomorrow > 0
        return when (m.kind) {
            WidgetKind.SETUP -> Line(if (wide) "Open to set up your plan" else "Open Exhale to set up")
            WidgetKind.ARMED -> Line("Tap again to log", Tone.STRONG)
            WidgetKind.LOGGED -> Line("Logged at", strong = t(m.loggedAt))
            WidgetKind.NOTHING_YET -> Line("Nothing logged yet", Tone.OXY)
            WidgetKind.WAITING, WidgetKind.EARNED -> when {
                borrowed -> Line("${m.borrowingFromTomorrow} borrowed from tomorrow", Tone.FLARE)
                m.kind == WidgetKind.WAITING -> Line("Next one at", strong = t(m.nextAt))
                else -> Line("Next one earned", Tone.OXY, tail = if (wide && m.lastAt != null) " · last ${t(m.lastAt)}" else "")
            }
        }
    }

    /** What TalkBack reads for the ring (2×2) or the ember button (4×1). */
    fun tapLabel(m: WidgetModel, zone: ZoneId, h24: Boolean): String = when (m.kind) {
        WidgetKind.ARMED -> "Tap again to log"
        else -> "Today ${m.count} of ${m.allowance}. ${line(m, false, zone, h24).text}. Tap twice to log."
    }
}
