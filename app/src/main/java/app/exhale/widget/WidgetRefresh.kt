package app.exhale.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import app.exhale.ExhaleApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * When the widgets redraw. A widget can't tick, so it redraws only when it would look different
 * ([app.exhale.domain.WidgetModel.refreshAt]: a ring step, the moment the next one is earned,
 * midnight), when data changes (ExhaleRepository's `onWrite`), and when the clock or time zone
 * changes. The scheduled redraw is an inexact alarm: no exact-alarm permission, and a few minutes
 * late is fine because the widget shows clock times, not a countdown.
 */
object WidgetRefresh {
    private const val ACTION = "app.exhale.widget.REFRESH"

    /** Redraws every Exhale widget with the time now. */
    suspend fun refreshAll(context: Context) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(ExhaleWidget::class.java)
        ids.forEach { redraw(context, it) }
    }

    fun refreshAllAsync(app: ExhaleApp) {
        app.scope.launch { runCatching { refreshAll(app) } }
    }

    /** Redraws [id] once [wait] has passed: the end of an armed or Undo window. */
    fun after(app: ExhaleApp, id: GlanceId, wait: Duration) {
        app.scope.launch {
            delay(wait.toMillis() + 150)
            runCatching { redraw(app, id) }
        }
    }

    private suspend fun redraw(context: Context, id: GlanceId) {
        updateAppWidgetState(context, id) { it[WidgetState.DRAWN_AT] = System.currentTimeMillis() }
        ExhaleWidget().update(context, id)
    }

    /** Asks for a redraw at [at]. One alarm for all widgets: a later call replaces it. */
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
                WidgetRefresh.refreshAll(app)
            } finally {
                done.finish()
            }
        }
    }
}
