package app.exhale.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.exhale.domain.ReminderKind
import kotlinx.coroutines.flow.first
import java.time.Instant

private val Context.reminderStore by preferencesDataStore(name = "reminders")

/**
 * When the last reminder of each kind was due and dealt with (posted, or skipped as no longer
 * fitting), so it is never posted twice. Bookkeeping only: not a setting, never shown.
 */
class ReminderStore(private val context: Context) {
    private fun key(kind: ReminderKind) = longPreferencesKey("handled_${kind.name.lowercase()}")

    suspend fun handled(kind: ReminderKind): Instant? =
        context.reminderStore.data.first()[key(kind)]?.let(Instant::ofEpochMilli)

    suspend fun markHandled(kind: ReminderKind, at: Instant) {
        context.reminderStore.edit { it[key(kind)] = at.toEpochMilli() }
    }
}
