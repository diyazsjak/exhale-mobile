package app.exhale.data

import app.exhale.domain.DemoData
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

/** Single entry point to local data. Everything stays on device. */
class ExhaleRepository(
    private val db: ExhaleDatabase,
    private val settingsStore: SettingsStore,
) {
    val settings: Flow<UserSettings> = settingsStore.settings

    val logs: Flow<List<SmokeLog>> = db.logs().observeAll().map { list -> list.map { it.toDomain() } }

    val urges: Flow<List<Urge>> = db.urges().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun saveSettings(s: UserSettings) = settingsStore.save(s)

    suspend fun logCigarette(trigger: Trigger? = null, fromUrge: Boolean = false): Long =
        db.logs().insert(SmokeLog(0, Instant.now(), trigger, fromUrge).toEntity())

    suspend fun undoLog(id: Long) = db.logs().delete(id)

    suspend fun tagLog(id: Long, trigger: Trigger?) = db.logs().setTrigger(id, trigger?.name)

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
    /** Replaces everything with 60 days of demo history. Appearance ([keep]'s theme and home) stays as it is. */
    suspend fun seedDemo(keep: UserSettings) {
        clearHistory()
        val zone = ZoneId.systemDefault()
        val demo = DemoData.generate(LocalDate.now(zone), Instant.now(), zone)
        saveSettings(demo.settings.copy(theme = keep.theme, home = keep.home))
        db.logs().insertAll(demo.logs.map { it.toEntity() })
        db.urges().insertAll(demo.urges.map { it.toEntity() })
    }

    private suspend fun clearHistory() {
        db.logs().clear()
        db.urges().clear()
    }
}
