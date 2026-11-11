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
 * No exact-alarm permission. A busy-hour nudge only helps before its hour, so its alarm goes off within
 * a 10-minute window (the shortest Android allows); the phone is in use at those hours, so Doze rarely
 * holds it. The others can come a while late, so theirs also go off in Doze.
 *
 * While Android doesn't let Exhale post, nothing is scheduled and nothing is used up: the reminders
 * are worked out again when Exhale next comes to the front.
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
        val on = if (ReminderNotifier.allowed(app)) s.reminders else emptySet()
        (ReminderKind.entries - on).forEach { cancel(app, it) }
        if (on.isEmpty()) return
        val logs = repo.logs.first()
        val urges = repo.urges.first()
        val handled = repo.remindersHandled()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val runs = on.associateWith { Reminders.due(it, s, logs, urges, handled[it], now, zone) }
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
        if (kind == ReminderKind.BUSY_HOUR) {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), BUSY_WINDOW_MS, pending(context, kind))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(context, kind))
        }
    }

    /** Within [Reminders.BUSY_LEAD], so a nudge still comes before its hour. */
    private const val BUSY_WINDOW_MS = 10 * 60_000L

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
