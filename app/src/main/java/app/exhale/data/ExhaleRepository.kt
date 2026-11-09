package app.exhale.data

import app.exhale.domain.DemoData
import app.exhale.domain.SmokeLog
import app.exhale.domain.Trigger
import app.exhale.domain.TriggerEntry
import app.exhale.domain.Triggers
import app.exhale.domain.Urge
import app.exhale.domain.UrgeOutcome
import app.exhale.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Single entry point to local data. Everything stays on device. */
class ExhaleRepository(
    private val db: ExhaleDatabase,
    private val settingsStore: SettingsStore,
) {
    val settings: Flow<UserSettings> = settingsStore.settings

    /** The user's trigger list: shown ones in chip order, then hidden ones. */
    val triggers: Flow<List<TriggerEntry>> = db.triggers().observeAll().map { list -> list.map { it.toDomain() } }

    /** Labels by key. Reordering or hiding changes none, so history isn't mapped again for them. */
    private val triggersByKey: Flow<Map<String, Trigger>> =
        triggers.map { list -> list.associate { it.key to it.trigger } }.distinctUntilChanged()

    val logs: Flow<List<SmokeLog>> =
        combine(db.logs().observeAll(), triggersByKey) { list, byKey -> list.map { it.toDomain(byKey) } }

    val urges: Flow<List<Urge>> =
        combine(db.urges().observeAll(), triggersByKey) { list, byKey -> list.map { it.toDomain(byKey) } }

    suspend fun saveTriggers(list: List<TriggerEntry>) =
        db.triggers().replaceAll(list.mapIndexed { i, e -> e.toEntity(i) })

    suspend fun saveSettings(s: UserSettings) = settingsStore.save(s)

    suspend fun logCigarette(trigger: Trigger? = null, fromUrge: Boolean = false): Long =
        db.logs().insert(SmokeLog(0, Instant.now(), trigger, fromUrge).toEntity())

    suspend fun undoLog(id: Long) = db.logs().delete(id)

    /** Moves log [id] to [at] and sets its trigger (null clears it): the day list's edit. */
    suspend fun editLog(id: Long, at: Instant, trigger: Trigger?) = db.logs().update(id, at.toEpochMilli(), trigger?.key)

    /** How many logs and cravings are tagged with [key]. */
    suspend fun uses(key: String): Int = db.logs().countTagged(key)

    suspend fun tagLog(id: Long, trigger: Trigger?) = db.logs().setTrigger(id, trigger?.key)

    /** Sets craving wave [id]'s trigger (null clears it). Its time and length stay as they were. */
    suspend fun tagUrge(id: Long, trigger: Trigger?) = db.urges().setTrigger(id, trigger?.key)

    suspend fun deleteUrge(id: Long) = db.urges().delete(id)

    suspend fun recordUrge(started: Instant, ended: Instant, outcome: UrgeOutcome, trigger: Trigger?) {
        db.urges().insert(Urge(0, started, ended, outcome, trigger).toEntity())
    }

    suspend fun countToday(zone: ZoneId = ZoneId.systemDefault()): Int {
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return db.logs().countBetween(start, start + 86_400_000L)
    }

    suspend fun eraseAll() {
        clearHistory()
        settingsStore.clear()
    }

    /** Replaces everything with [DemoData]: a matching onboarded profile and 60 days of history. */
    suspend fun seedDemo() {
        clearHistory()
        val zone = ZoneId.systemDefault()
        val demo = DemoData.generate(LocalDate.now(zone), Instant.now(), zone, current = settings.first())
        saveSettings(demo.settings)
        db.logs().insertAll(demo.logs.map { it.toEntity() })
        db.urges().insertAll(demo.urges.map { it.toEntity() })
    }

    private suspend fun clearHistory() {
        db.logs().clear()
        db.urges().clear()
        // Erasing or loading demo data starts the trigger list over from the built-ins.
        saveTriggers(Triggers.defaults)
    }
}
