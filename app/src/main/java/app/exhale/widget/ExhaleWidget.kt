package app.exhale.widget

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.exhale.ExhaleApp
import app.exhale.domain.TapResult
import app.exhale.domain.TodayPlan
import app.exhale.domain.Widget
import app.exhale.domain.WidgetTap
import app.exhale.ui.components.Haptics
import app.exhale.ui.theme.exColors
import app.exhale.ui.theme.isDark
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * The home-screen widget (SPEC §3.9): today's count, when the next one is earned, and two taps to
 * log. One widget, offered in the picker as 2×2 ([ExhaleWidgetReceiver]) and 4×1
 * ([ExhaleWideWidgetReceiver]); its layout follows its size.
 */
class ExhaleWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) = coroutineScope {
        val repo = (context.applicationContext as ExhaleApp).repository
        // One subscription each for the session, waiting for the first value before the first draw.
        val settingsFlow = repo.settings.stateIn(this)
        val logsFlow = repo.logs.stateIn(this)
        provideContent {
            val settings by settingsFlow.collectAsState()
            val logs by logsFlow.collectAsState()
            // Read so a redraw (WidgetRefresh.clock) recomposes with the time now.
            WidgetRefresh.clock.collectAsState().value
            val tap = WidgetState.tap(currentState<Preferences>())
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val model = Widget.model(settings, logs, tap, now, zone)
            LaunchedEffect(model.refreshAt) { WidgetRefresh.schedule(context, model.refreshAt) }
            val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            WidgetContent(model, exColors(settings.theme.isDark(systemDark)), zone, DateFormat.is24HourFormat(context))
        }
    }
}

/** The 2×2 entry in the widget picker. */
open class ExhaleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExhaleWidget()
}

/** The 4×1 entry in the widget picker: the same widget, starting short and wide. */
class ExhaleWideWidgetReceiver : ExhaleWidgetReceiver()

/** Each widget's own tap state ([WidgetTap]), kept in Glance's per-widget preferences. */
internal object WidgetState {
    /** Taps and Undos run one at a time: each reads this state, acts, then writes it. */
    val lock = Mutex()

    private val ARMED_UNTIL = longPreferencesKey("armed_until")
    private val LOGGED_ID = longPreferencesKey("logged_id")
    private val UNDO_UNTIL = longPreferencesKey("undo_until")

    fun tap(p: Preferences) = WidgetTap(
        armedUntil = p[ARMED_UNTIL]?.let(Instant::ofEpochMilli),
        loggedId = p[LOGGED_ID],
        undoUntil = p[UNDO_UNTIL]?.let(Instant::ofEpochMilli),
    )

    suspend fun read(context: Context, id: GlanceId): WidgetTap =
        tap(getAppWidgetState(context, PreferencesGlanceStateDefinition, id))

    /** Writes [tap] as widget [id]'s state. It shows from the widget's next redraw. */
    suspend fun write(context: Context, id: GlanceId, tap: WidgetTap) {
        updateAppWidgetState(context, id) { p: MutablePreferences ->
            p.setOrRemove(ARMED_UNTIL, tap.armedUntil?.toEpochMilli())
            p.setOrRemove(LOGGED_ID, tap.loggedId)
            p.setOrRemove(UNDO_UNTIL, tap.undoUntil?.toEpochMilli())
        }
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<Long>, value: Long?) {
        if (value == null) remove(key) else this[key] = value
    }
}

/**
 * A tap on the ring (2×2) or the ember button (4×1): the first arms, the second logs. Taps are handled
 * one at a time, so two quick taps on an armed widget can't both log.
 */
class WidgetTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        WidgetState.lock.withLock { tap(context, glanceId) }

    private suspend fun tap(context: Context, glanceId: GlanceId) {
        val app = context.applicationContext as ExhaleApp
        val now = Instant.now()
        when (val r = Widget.onTap(WidgetState.read(context, glanceId), now)) {
            TapResult.Ignore -> Unit
            is TapResult.Arm -> {
                WidgetState.write(context, glanceId, WidgetTap(armedUntil = r.until))
                ExhaleWidget().update(context, glanceId)
                WidgetRefresh.after(app, glanceId, Widget.ARM_WINDOW)
            }
            TapResult.Log -> {
                val repo = app.repository
                val goesOver = TodayPlan.of(repo.settings.first(), repo.logs.first(), now, ZoneId.systemDefault()).overAllowance
                // Disarm first, so the insert's redraw doesn't still ask for a second tap.
                WidgetState.write(context, glanceId, WidgetTap())
                val id = repo.logCigarette(at = now)
                // Same meaning as on Ember. Android may mute vibration for an app in the background.
                Haptics(context, null).run { if (goesOver) loggedOver() else logged() }
                // The insert already redraws every widget (ExhaleRepository.onWrite), maybe before this
                // state is written, so this one is redrawn once more with its Undo.
                WidgetState.write(context, glanceId, WidgetTap(loggedId = id, undoUntil = now.plus(Widget.UNDO_WINDOW)))
                ExhaleWidget().update(context, glanceId)
                WidgetRefresh.after(app, glanceId, Widget.UNDO_WINDOW)
            }
        }
    }
}

/** Undo for the widget's own last log, only within its window: an Undo still drawn later does nothing. */
class WidgetUndoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        WidgetState.lock.withLock { undo(context, glanceId) }

    private suspend fun undo(context: Context, glanceId: GlanceId) {
        val app = context.applicationContext as ExhaleApp
        val logged = WidgetState.read(context, glanceId).takeIf { it.undoShown(Instant.now()) }?.loggedId
        WidgetState.write(context, glanceId, WidgetTap())
        WidgetRefresh.cancel(glanceId)
        // Deleting redraws every widget (ExhaleRepository.onWrite); with nothing to undo, redraw this one.
        if (logged != null) app.repository.undoLog(logged) else ExhaleWidget().update(context, glanceId)
    }
}
