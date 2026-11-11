package app.exhale.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.exhale.ExhaleApp
import app.exhale.domain.ReminderKind
import app.exhale.domain.Reminders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps one alarm per reminder kind that is turned on, set for its next reminder ([Reminders.next]).
 * [run] posts whatever is due, then sets the alarms again. It runs when an alarm goes off, after
 * every log or settings change (ExhaleRepository's `onWrite`), when Exhale opens, after a restart
 * or an update, and when the clock or time zone changes.
 *
 * The alarms are inexact (no exact-alarm permission) but go off in Doze. A reminder the phone held
 * back is still posted within its kind's [ReminderKind.late]; after that it is dropped. Each due
 * reminder is marked handled whether it was posted or skipped ([Reminders.shouldPost]), so none is
 * posted twice.
 */
object ReminderScheduler {
    private const val ACTION = "app.exhale.notify.REMIND"
    private val lock = Mutex()

    fun runAsync(app: ExhaleApp) {
        app.scope.launch { runCatching { run(app) } }
    }

    suspend fun run(app: ExhaleApp) = lock.withLock {
        val repo = app.repository
        val s = repo.settings.first()
        val logs = repo.logs.first()
        val urges = repo.urges.first()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        for (kind in ReminderKind.entries) {
            if (kind !in s.reminders) {
                cancel(app, kind)
                continue
            }
            val handled = app.reminders.handled(kind)
            var after = listOfNotNull(now.minus(kind.late), handled).max()
            var r = Reminders.next(kind, s, logs, now, after, zone)
            while (r != null && !r.at.isAfter(now)) {
                if (Reminders.shouldPost(r, logs, urges, now)) ReminderNotifier.post(app, r)
                app.reminders.markHandled(kind, r.at)
                after = r.at
                r = Reminders.next(kind, s, logs, now, after, zone)
            }
            if (r == null) cancel(app, kind) else schedule(app, kind, r.at)
        }
    }

    private fun pending(context: Context, kind: ReminderKind): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context, kind.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun schedule(context: Context, kind: ReminderKind, at: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(context, kind))
    }

    private fun cancel(context: Context, kind: ReminderKind) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, kind))
    }
}

/**
 * Runs [ReminderScheduler.run] on a reminder's alarm, after a restart or an update of Exhale (which
 * clear alarms), and when the clock or time zone changes.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ExhaleApp
        val done = goAsync()
        app.scope.launch {
            try {
                runCatching { ReminderScheduler.run(app) }
            } finally {
                done.finish()
            }
        }
    }
}
