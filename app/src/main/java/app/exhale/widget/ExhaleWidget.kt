package app.exhale.widget

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.exhale.domain.ThemeMode
import app.exhale.domain.TodayPlan
import app.exhale.domain.Widget
import app.exhale.domain.WidgetTap
import app.exhale.ui.components.Haptics
import app.exhale.ui.theme.DarkColors
import app.exhale.ui.theme.ExColors
import app.exhale.ui.theme.LightColors
import kotlinx.coroutines.flow.first
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as ExhaleApp).repository
        val settings0 = repo.settings.first()
        val logs0 = repo.logs.first()
        provideContent {
            val settings by repo.settings.collectAsState(settings0)
            val logs by repo.logs.collectAsState(logs0)
            // Every redraw (WidgetRefresh) writes this widget's state, so reading it here recomposes
            // with a fresh "now".
            val tap = WidgetState.tap(currentState<Preferences>())
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val model = Widget.model(settings, logs, tap, now, zone)
            LaunchedEffect(model.refreshAt) { WidgetRefresh.schedule(context, model.refreshAt) }
            WidgetContent(model, colors(context, settings.theme), zone, DateFormat.is24HourFormat(context))
        }
    }

    /** The app's theme setting, like MainActivity; "System" is read when the widget is drawn. */
    private fun colors(context: Context, mode: ThemeMode): ExColors {
        val dark = when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM ->
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        return if (dark) DarkColors else LightColors
    }
}

/** The 2×2 entry in the widget picker. */
class ExhaleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExhaleWidget()
}

/** The 4×1 entry in the widget picker: the same widget, starting short and wide. */
class ExhaleWideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExhaleWidget()
}

/** Each widget's own tap state ([WidgetTap]), kept in Glance's per-widget preferences. */
internal object WidgetState {
    val ARMED_UNTIL = longPreferencesKey("armed_until")
    val LOGGED_ID = longPreferencesKey("logged_id")
    val LOGGED_AT = longPreferencesKey("logged_at")
    val UNDO_UNTIL = longPreferencesKey("undo_until")
    /** Bumped on every redraw so the content recomposes with the time of the redraw. */
    val DRAWN_AT = longPreferencesKey("drawn_at")

    fun tap(p: Preferences) = WidgetTap(
        armedUntil = p[ARMED_UNTIL]?.let(Instant::ofEpochMilli),
        loggedId = p[LOGGED_ID],
        loggedAt = p[LOGGED_AT]?.let(Instant::ofEpochMilli),
        undoUntil = p[UNDO_UNTIL]?.let(Instant::ofEpochMilli),
    )
}

/** A tap on the ring (2×2) or the ember button (4×1): the first arms, the second logs. */
class WidgetTapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as ExhaleApp
        val now = Instant.now()
        val tap = WidgetState.tap(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
        when (val r = Widget.onTap(tap, now)) {
            TapResult.Ignore -> Unit
            is TapResult.Arm -> {
                updateAppWidgetState(context, glanceId) {
                    it[WidgetState.ARMED_UNTIL] = r.until.toEpochMilli()
                    it[WidgetState.DRAWN_AT] = now.toEpochMilli()
                }
                ExhaleWidget().update(context, glanceId)
                WidgetRefresh.after(app, glanceId, Widget.ARM_WINDOW)
            }
            TapResult.Log -> {
                val repo = app.repository
                val zone = ZoneId.systemDefault()
                val goesOver = TodayPlan.of(repo.settings.first(), repo.logs.first(), now, zone).overAllowance
                val id = repo.logCigarette(at = now)
                updateAppWidgetState(context, glanceId) {
                    it.remove(WidgetState.ARMED_UNTIL)
                    it[WidgetState.LOGGED_ID] = id
                    it[WidgetState.LOGGED_AT] = now.toEpochMilli()
                    it[WidgetState.UNDO_UNTIL] = now.plus(Widget.UNDO_WINDOW).toEpochMilli()
                    it[WidgetState.DRAWN_AT] = now.toEpochMilli()
                }
                ExhaleWidget().update(context, glanceId)
                // Same meaning as on Ember. Android may mute vibration for an app in the background.
                Haptics(context, null).run { if (goesOver) loggedOver() else logged() }
                WidgetRefresh.after(app, glanceId, Widget.UNDO_WINDOW)
            }
        }
    }
}

/** Undo for the widget's own last log. */
class WidgetUndoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as ExhaleApp
        val tap = WidgetState.tap(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
        updateAppWidgetState(context, glanceId) {
            it.remove(WidgetState.LOGGED_ID)
            it.remove(WidgetState.LOGGED_AT)
            it.remove(WidgetState.UNDO_UNTIL)
            it[WidgetState.DRAWN_AT] = System.currentTimeMillis()
        }
        tap.loggedId?.let { app.repository.undoLog(it) }
        ExhaleWidget().update(context, glanceId)
    }
}
