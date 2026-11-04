package app.exhale.data

import app.exhale.domain.SmokeLog
import app.exhale.domain.Trigger
import app.exhale.domain.Urge
import app.exhale.domain.UrgeOutcome
import app.exhale.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Single entry point to local data. Everything stays on device. */
class ExhaleRepository(
    private val db: ExhaleDatabase,
    private val settingsStore: SettingsStore,
) {
    val settings: Flow<UserSettings> = settingsStore.settings

    val logs: Flow<List<SmokeLog>> = db.logs().observeAll().map { list -> list.map { it.toDomain() } }

    val urges: Flow<List<Urge>> = db.urges().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun saveSettings(s: UserSettings) = settingsStore.save(s)

    suspend fun logCigarette(
        at: Instant = Instant.now(),
        trigger: Trigger? = null,
        fromUrge: Boolean = false,
    ): Long = db.logs().insert(SmokeLogEntity(atMillis = at.toEpochMilli(), trigger = trigger?.name, fromUrge = fromUrge))

    suspend fun undoLog(id: Long) = db.logs().delete(id)

    suspend fun tagLog(id: Long, trigger: Trigger?) = db.logs().setTrigger(id, trigger?.name)

    suspend fun recordUrge(started: Instant, ended: Instant, outcome: UrgeOutcome, trigger: Trigger?) {
        db.urges().insert(
            UrgeEntity(
                startedMillis = started.toEpochMilli(),
                endedMillis = ended.toEpochMilli(),
                outcome = outcome.name,
                trigger = trigger?.name,
            )
        )
    }

    suspend fun countToday(zone: ZoneId = ZoneId.systemDefault()): Int {
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return db.logs().countBetween(start, start + 86_400_000L)
    }

    suspend fun eraseAll() {
        db.logs().clear()
        db.urges().clear()
        settingsStore.clear()
    }

    /**
     * Seeds 30 days of plausible example data and a matching onboarded profile.
     * Used by "Load demo data" so every screen has something to show.
     */
    suspend fun seedDemo(zone: ZoneId = ZoneId.systemDefault(), random: Random = Random(42)) {
        db.logs().clear()
        db.urges().clear()
        val today = LocalDate.now(zone)
        // 60 days, so Rhythm has a "30 days before" to compare with.
        val start = today.minusDays(59)
        saveSettings(
            UserSettings(
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
        )
        // Hour weights: morning coffee spike, lunch, evening cluster.
        val weights = floatArrayOf(
            .1f, 0f, 0f, 0f, 0f, 0f, .2f, .9f, 1.6f, .9f, .5f, .6f,
            .7f, 1.3f, .6f, .5f, .7f, .8f, 1.2f, 1.1f, .9f, 1f, .7f, .3f,
        )
        val triggerFor = { h: Int ->
            when (h) {
                in 6..9 -> Trigger.COFFEE
                in 12..14 -> Trigger.AFTER_FOOD
                in 15..17 -> Trigger.STRESS
                in 19..22 -> if (random.nextBoolean()) Trigger.DRINKS else Trigger.SOCIAL
                else -> Trigger.entries[random.nextInt(Trigger.entries.size)]
            }
        }
        val now = Instant.now()
        val logs = mutableListOf<SmokeLogEntity>()
        val urges = mutableListOf<UrgeEntity>()
        for (i in 0..59) {
            val day = start.plusDays(i.toLong())
            // Following the plan down from ~16 to ~8: mostly within, now and then over.
            val target = (16 - i / 7) + random.nextInt(-2, 2)
            repeat(target) {
                val hour = pickWeighted(weights, random)
                val t = day.atTime(hour, random.nextInt(60)).atZone(zone).toInstant()
                if (t.isBefore(now)) {
                    val tagged = random.nextFloat() < 0.75f
                    logs += SmokeLogEntity(atMillis = t.toEpochMilli(), trigger = if (tagged) triggerFor(hour).name else null, fromUrge = false)
                }
            }
            repeat(random.nextInt(0, 3)) {
                val t = day.atTime(10 + random.nextInt(12), random.nextInt(60)).atZone(zone).toInstant()
                if (t.isBefore(now)) {
                    urges += UrgeEntity(
                        startedMillis = t.toEpochMilli(),
                        endedMillis = t.toEpochMilli() + 240_000,
                        outcome = if (random.nextFloat() < 0.7f) UrgeOutcome.SURVIVED.name else UrgeOutcome.SMOKED.name,
                        trigger = Trigger.STRESS.name,
                    )
                }
            }
        }
        db.logs().insertAll(logs.sortedBy { it.atMillis })
        db.urges().insertAll(urges)
    }

    private fun pickWeighted(w: FloatArray, r: Random): Int {
        val total = w.sum()
        var x = r.nextFloat() * total
        for (i in w.indices) { x -= w[i]; if (x <= 0f) return i }
        return w.lastIndex
    }
}

private fun SmokeLogEntity.toDomain() = SmokeLog(
    id = id,
    at = Instant.ofEpochMilli(atMillis),
    trigger = trigger?.let { runCatching { Trigger.valueOf(it) }.getOrNull() },
    fromUrge = fromUrge,
)

private fun UrgeEntity.toDomain() = Urge(
    id = id,
    startedAt = Instant.ofEpochMilli(startedMillis),
    endedAt = Instant.ofEpochMilli(endedMillis),
    outcome = runCatching { UrgeOutcome.valueOf(outcome) }.getOrDefault(UrgeOutcome.SURVIVED),
    trigger = trigger?.let { runCatching { Trigger.valueOf(it) }.getOrNull() },
)
