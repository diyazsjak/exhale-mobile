package app.exhale.domain

import java.time.Duration

/**
 * Recovery milestones after the last cigarette.
 * Source: WHO, "Health benefits of smoking cessation". Lower bound of each range is used.
 */
data class Milestone(val after: Duration, val label: String, val text: String)

object Milestones {
    val all = listOf(
        Milestone(Duration.ofMinutes(20), "20 min", "Heart rate and blood pressure drop"),
        Milestone(Duration.ofHours(12), "12 h", "Carbon monoxide in the blood drops to normal"),
        Milestone(Duration.ofDays(14), "2–12 wk", "Circulation improves and lung function increases"),
        Milestone(Duration.ofDays(30), "1–9 mo", "Coughing and shortness of breath decrease"),
        Milestone(Duration.ofDays(365), "1 yr", "Coronary heart disease risk is about half a smoker's"),
        Milestone(Duration.ofDays(365L * 5), "5–15 yr", "Stroke risk falls to that of a non-smoker"),
        Milestone(Duration.ofDays(365L * 10), "10 yr", "Lung cancer death risk is about half a smoker's"),
        Milestone(Duration.ofDays(365L * 15), "15 yr", "Heart disease risk is that of a non-smoker"),
    )

    /** 0..1 progress used by the particle field: how far toward the 12 h milestone. */
    fun clearFraction(sinceLast: Duration?): Float {
        if (sinceLast == null) return 1f
        val target = Duration.ofHours(12).toMinutes().toFloat()
        return (sinceLast.toMinutes() / target).coerceIn(0.05f, 1f)
    }
}
