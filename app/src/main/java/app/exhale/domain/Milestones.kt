package app.exhale.domain

import java.time.Duration
import kotlin.math.ln

/**
 * One recovery milestone after a cigarette. Source: WHO, "Health benefits of smoking cessation".
 * It counts as reached at the lower bound of WHO's range ([after]); [range] is the range as WHO
 * gives it, when it is one.
 */
data class Milestone(
    val after: Duration,
    /** When it is reached, in words: "20 minutes", "2 weeks". */
    val label: String,
    /** A shorter [label] for tight spots such as the sky: "20 min", "2 weeks". */
    val shortLabel: String,
    /** WHO's range when it gives one: "2–12 weeks". */
    val range: String?,
    /** What happens: "Heart rate and blood pressure drop". */
    val text: String,
    /** Two or three words for the sky: "Heart rate settles". */
    val short: String,
    /** What the body is doing while this is the next milestone: shown as "right now". */
    val underway: String,
)

object Milestones {
    val all = listOf(
        Milestone(
            Duration.ofMinutes(20), "20 minutes", "20 min", null,
            "Heart rate and blood pressure drop", "Heart rate settles",
            "Your heart rate and blood pressure are starting to come down.",
        ),
        Milestone(
            Duration.ofHours(12), "12 hours", "12 h", null,
            "Carbon monoxide back to normal", "Carbon monoxide normal",
            "Carbon monoxide is leaving your blood and oxygen is taking its place.",
        ),
        Milestone(
            Duration.ofDays(14), "2 weeks", "2 weeks", "2–12 weeks",
            "Circulation and lung function improve", "Circulation improves",
            "Your blood carries oxygen normally again. Circulation is starting to improve.",
        ),
        Milestone(
            Duration.ofDays(30), "1 month", "1 month", "1–9 months",
            "Coughing and shortness of breath ease", "Easier breathing",
            "Circulation and lung function are improving.",
        ),
        Milestone(
            Duration.ofDays(365), "1 year", "1 year", null,
            "Heart disease risk about half a smoker's", "Heart risk halves",
            "Your lungs are clearing: less coughing, easier breathing.",
        ),
        Milestone(
            Duration.ofDays(365L * 5), "5 years", "5 years", "5–15 years",
            "Stroke risk like a non-smoker's", "Stroke risk",
            "Your heart disease risk is about half a smoker's and still falling.",
        ),
        Milestone(
            Duration.ofDays(365L * 10), "10 years", "10 years", null,
            "Lung cancer death risk about half a smoker's", "Lung cancer risk",
            "Your stroke risk is falling toward a non-smoker's.",
        ),
        Milestone(
            Duration.ofDays(365L * 15), "15 years", "15 years", null,
            "Heart disease risk like a non-smoker's", "Heart like a non-smoker's",
            "Your lung cancer risk keeps falling.",
        ),
    )

    /** Said once every milestone is reached. */
    const val ALL_REACHED = "Every milestone on the list is behind you."

    /** The first milestone not yet reached [since] the last cigarette; null with no logs or when all are. */
    fun next(since: Duration?): Milestone? = since?.let { s -> all.firstOrNull { s < it.after } }

    /** How many milestones [since] has reached, in order. */
    fun reached(since: Duration?): Int = if (since == null) 0 else all.count { since >= it.after }

    /**
     * Where [since] sits on the path from the last cigarette (0) through each milestone (1..8):
     * linear up to the first milestone, then on a log scale between milestones, so the dot moves
     * visibly in the first hours instead of crawling toward years. Capped at the last milestone.
     */
    fun position(since: Duration?): Float {
        if (since == null) return 0f
        val s = since.toMillis().coerceAtLeast(0).toDouble()
        val first = all.first().after.toMillis().toDouble()
        if (s < first) return (s / first).toFloat()
        for (i in 1 until all.size) {
            val a = all[i - 1].after.toMillis().toDouble()
            val b = all[i].after.toMillis().toDouble()
            if (s < b) return (i + ln(s / a) / ln(b / a)).toFloat()
        }
        return all.size.toFloat()
    }

    /** 0..1 progress used by the particle field: how far toward the 12 h milestone, at least 0.05; 1 with no logs. */
    fun clearFraction(sinceLast: Duration?): Float {
        if (sinceLast == null) return 1f
        return (sinceLast.toMinutes() / CLEAR_MINUTES).coerceIn(0.05f, 1f)
    }

    /** The 12 h milestone (carbon monoxide back to normal): the particle field is clear once a gap reaches it. */
    private val CLEAR_MINUTES = Duration.ofHours(12).toMinutes().toFloat()
}
