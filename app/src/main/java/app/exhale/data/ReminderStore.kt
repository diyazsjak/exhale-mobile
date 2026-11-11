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
 * fitting), so it is never posted twice. Bookkeeping only: not a setting, never shown. It says when
 * milestones and the goal were reached, so erasing everything clears it too.
 */
class ReminderStore(private val context: Context) {
    private fun key(kind: ReminderKind) = longPreferencesKey("handled_${kind.name.lowercase()}")

    suspend fun handled(): Map<ReminderKind, Instant> {
        val p = context.reminderStore.data.first()
        return ReminderKind.entries.mapNotNull { k -> p[key(k)]?.let { k to Instant.ofEpochMilli(it) } }.toMap()
    }

    suspend fun markHandled(handled: Map<ReminderKind, Instant>) {
        if (handled.isEmpty()) return
        context.reminderStore.edit { p -> handled.forEach { (k, at) -> p[key(k)] = at.toEpochMilli() } }
    }

    suspend fun clear() {
        context.reminderStore.edit { it.clear() }
    }
}
