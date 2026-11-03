package app.exhale.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.exhale.domain.TaperType
import app.exhale.domain.ThemeMode
import app.exhale.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val baseline = intPreferencesKey("baseline_per_day")
        val perPack = intPreferencesKey("cigs_per_pack")
        val packPrice = longPreferencesKey("pack_price_minor")
        val currency = stringPreferencesKey("currency")
        val taper = stringPreferencesKey("taper")
        val startAllowance = intPreferencesKey("start_allowance")
        val planStart = longPreferencesKey("plan_start_epoch_day")
        val waking = intPreferencesKey("waking_minutes")
        val goalName = stringPreferencesKey("goal_name")
        val goalMinor = longPreferencesKey("goal_minor")
        val theme = stringPreferencesKey("theme")
        val borrowSeen = longPreferencesKey("borrow_notice_seen_day")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun save(s: UserSettings) {
        context.dataStore.edit { p ->
            p[K.onboarded] = s.onboarded
            p[K.baseline] = s.baselinePerDay
            p[K.perPack] = s.cigsPerPack
            p[K.packPrice] = s.packPriceMinor
            p[K.currency] = s.currencyCode
            p[K.taper] = s.taper.name
            p[K.startAllowance] = s.startAllowance
            p[K.planStart] = s.planStart.toEpochDay()
            p[K.waking] = s.wakingMinutes
            p[K.goalName] = s.goalName
            p[K.goalMinor] = s.goalMinor
            p[K.theme] = s.theme.name
            p[K.borrowSeen] = s.borrowNoticeSeenDay
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings(): UserSettings {
        val d = UserSettings()
        return UserSettings(
            onboarded = this[K.onboarded] ?: false,
            baselinePerDay = this[K.baseline] ?: d.baselinePerDay,
            cigsPerPack = this[K.perPack] ?: d.cigsPerPack,
            packPriceMinor = this[K.packPrice] ?: d.packPriceMinor,
            currencyCode = this[K.currency] ?: d.currencyCode,
            taper = this[K.taper]?.let { runCatching { TaperType.valueOf(it) }.getOrNull() } ?: d.taper,
            startAllowance = this[K.startAllowance] ?: d.startAllowance,
            planStart = this[K.planStart]?.let { LocalDate.ofEpochDay(it) } ?: d.planStart,
            wakingMinutes = this[K.waking] ?: d.wakingMinutes,
            goalName = this[K.goalName] ?: d.goalName,
            goalMinor = this[K.goalMinor] ?: d.goalMinor,
            theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            borrowNoticeSeenDay = this[K.borrowSeen] ?: d.borrowNoticeSeenDay,
        )
    }
}
