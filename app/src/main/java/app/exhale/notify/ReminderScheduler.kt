package app.exhale.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.exhale.ExhaleApp
import app.exhale.domain.ReminderKind
import app.exhale.domain.Reminders
import app.exhale.launchAsync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps one alarm per reminder kind that is turned on, set for its next reminder. [run] posts
 * whatever is due ([Reminders.due]), then sets the alarms again. It runs when an alarm goes off,
 * after every log or settings change (ExhaleRepository's `onWrite`), when Exhale starts, after a
 * restart or an update, and when the clock or time zone changes.
 *
 * The alarms are inexact (no exact-alarm permission) but go off in Doze.
 */
object ReminderScheduler {
    private const val ACTION = "app.exhale.notify.REMIND"
    private val lock = Mutex()

    /** A run is waiting for the lock: more requests until it starts are covered by it. */
    private val queued = AtomicBoolean(false)

    /** Runs soon. Quick writes in a row share one run, which reads the data once it starts. */
    fun runAsync(app: ExhaleApp) {
        if (!queued.compareAndSet(false, true)) return
        app.scope.launch {
            lock.withLock {
                queued.set(false)
                runCatching { runLocked(app) }
            }
        }
    }

    suspend fun run(app: ExhaleApp) = lock.withLock { runLocked(app) }

    private suspend fun runLocked(app: ExhaleApp) {
        val repo = app.repository
        val s = repo.settings.first()
        val off = ReminderKind.entries - s.reminders
        off.forEach { cancel(app, it) }
        if (off.size == ReminderKind.entries.size) return
        val logs = repo.logs.first()
        val urges = repo.urges.first()
        val handled = repo.remindersHandled()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val runs = s.reminders.associateWith { Reminders.due(it, s, logs, urges, handled[it], now, zone) }
        // Marked handled before posting: if anything stops in between, one is missed rather than sent twice.
        repo.markRemindersHandled(runs.mapNotNull { (k, r) -> r.handled?.let { k to it } }.toMap())
        runs.forEach { (kind, run) ->
            run.post.forEach { ReminderNotifier.post(app, it) }
            if (run.next == null) cancel(app, kind) else schedule(app, kind, run.next.at)
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
    override fun onReceive(context: Context, intent: Intent) = launchAsync(context) { ReminderScheduler.run(it) }
}
