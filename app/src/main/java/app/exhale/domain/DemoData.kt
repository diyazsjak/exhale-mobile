package app.exhale.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Example data for "Load demo data": an onboarded profile with 60 days of plausible logs and urges, so
 * every screen has something to show (60, so Rhythm has a "30 days before" to compare with).
 * Ids are 0: the database assigns them.
 */
data class DemoData(val settings: UserSettings, val logs: List<SmokeLog>, val urges: List<Urge>) {
    companion object {
        private const val DAYS = 60
        private val URGE_LENGTH = Duration.ofMinutes(4)

        /** Hour weights: morning coffee spike, lunch, evening cluster. */
        private val HOUR_WEIGHTS = floatArrayOf(
            .1f, 0f, 0f, 0f, 0f, 0f, .2f, .9f, 1.6f, .9f, .5f, .6f,
            .7f, 1.3f, .6f, .5f, .7f, .8f, 1.2f, 1.1f, .9f, 1f, .7f, .3f,
        )

        /** Days run from [today] − 59 to [today]; nothing is placed after [now]. */
        fun generate(today: LocalDate, now: Instant, zone: ZoneId, random: Random = Random(42)): DemoData {
            val start = today.minusDays(DAYS - 1L)
            val settings = UserSettings(
                onboarded = true,
                baselinePerDay = 18,
                cigsPerPack = 20,
                packPriceMinor = 1000,
                currencyCode = "USD",
                startAllowance = 16,
                planStart = start,
                goalName = "New headphones",
                goalMinor = 59900,
            )
            fun triggerFor(hour: Int) = when (hour) {
                in 6..9 -> Trigger.COFFEE
                in 12..14 -> Trigger.AFTER_FOOD
                in 15..17 -> Trigger.STRESS
                in 19..22 -> if (random.nextBoolean()) Trigger.DRINKS else Trigger.SOCIAL
                else -> Trigger.entries[random.nextInt(Trigger.entries.size)]
            }
            val logs = mutableListOf<SmokeLog>()
            val urges = mutableListOf<Urge>()
            for (i in 0 until DAYS) {
                val day = start.plusDays(i.toLong())
                // Following the plan down from ~16 to ~8: mostly within, now and then over.
                val target = (16 - i / 7) + random.nextInt(-2, 2)
                repeat(target) {
                    val hour = pickWeighted(HOUR_WEIGHTS, random)
                    val at = day.atTime(hour, random.nextInt(60)).atZone(zone).toInstant()
                    if (at.isBefore(now)) {
                        val tagged = random.nextFloat() < 0.75f
                        logs += SmokeLog(0, at, if (tagged) triggerFor(hour) else null, fromUrge = false)
                    }
                }
                repeat(random.nextInt(0, 3)) {
                    val at = day.atTime(10 + random.nextInt(12), random.nextInt(60)).atZone(zone).toInstant()
                    if (at.isBefore(now)) {
                        val outcome = if (random.nextFloat() < 0.7f) UrgeOutcome.SURVIVED else UrgeOutcome.SMOKED
                        urges += Urge(0, at, at.plus(URGE_LENGTH), outcome, Trigger.STRESS)
                    }
                }
            }
            return DemoData(settings, logs.sortedBy { it.at }, urges)
        }

        private fun pickWeighted(weights: FloatArray, random: Random): Int {
            var x = random.nextFloat() * weights.sum()
            for (i in weights.indices) {
                x -= weights[i]
                if (x <= 0f) return i
            }
            return weights.lastIndex
        }
    }
}
