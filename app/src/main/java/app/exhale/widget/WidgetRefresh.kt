package app.exhale.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.appwidget.updateAll
import app.exhale.ExhaleApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * When the widgets redraw. A widget can't tick, so it redraws only when it would look different
 * ([app.exhale.domain.WidgetModel.refreshAt]: a ring step, the moment the next one is earned,
 * midnight), at the end of its armed or Undo window ([after]), when data changes (ExhaleRepository's
 * `onWrite`), and when the clock or time zone changes. The scheduled redraw is an inexact alarm: no
 * exact-alarm permission, and a few minutes late is fine because the widget shows clock times.
 */
object WidgetRefresh {
    private const val ACTION = "app.exhale.widget.REFRESH"

    private val tick = MutableStateFlow(Instant.now())

    /** When the widgets were last asked to redraw. A running widget recomposes when it changes. */
    val clock: StateFlow<Instant> get() = tick

    /** Each widget's pending end-of-window redraw, so a newer one replaces it. */
    private val pending = ConcurrentHashMap<GlanceId, Job>()

    /** Redraws every Exhale widget with the time now. */
    suspend fun refreshAll(context: Context) {
        tick.value = Instant.now()
        ExhaleWidget().updateAll(context)
    }

    fun refreshAllAsync(app: ExhaleApp) {
        app.scope.launch { runCatching { refreshAll(app) } }
    }

    /** Redraws [id] once [wait] has passed: the end of its armed or Undo window. */
    fun after(app: ExhaleApp, id: GlanceId, wait: Duration) {
        pending.put(id, app.scope.launch {
            delay(wait.toMillis() + 150)
            tick.value = Instant.now()
            runCatching { ExhaleWidget().update(app, id) }
        })?.cancel()
    }

    fun cancel(id: GlanceId) {
        pending.remove(id)?.cancel()
    }

    /** Asks for a redraw at [at]. One alarm for all widgets (they share the same plan): a later call replaces it. */
    fun schedule(context: Context, at: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.set(AlarmManager.RTC, at.toEpochMilli(), pending)
    }
}

/** Redraws the widgets on the scheduled alarm and when the clock, time zone or language changes. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ExhaleApp
        val done = goAsync()
        app.scope.launch {
            try {
                runCatching { WidgetRefresh.refreshAll(app) }
            } finally {
                done.finish()
            }
        }
    }
}
