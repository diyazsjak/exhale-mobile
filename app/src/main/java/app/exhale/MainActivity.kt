package app.exhale

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.exhale.data.enumOrNull
import app.exhale.domain.ReminderKind
import app.exhale.notify.ReminderScheduler
import app.exhale.widget.WidgetRefresh
import app.exhale.ui.ExhaleRoot
import app.exhale.ui.ExhaleViewModel
import app.exhale.ui.theme.ExhaleTheme
import app.exhale.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A tapped reminder, until the app has gone to its screen. */
    private val open = MutableStateFlow<ReminderKind?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only for a fresh start, not a rotation: the intent has been followed already.
        if (savedInstanceState == null) open.value = tapped(intent)
        setContent {
            val vm: ExhaleViewModel = viewModel(factory = ExhaleViewModel.Factory)
            val state by vm.state.collectAsStateWithLifecycle()
            val tapped by open.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = state.settings.theme.isDark(systemDark)
            // Status/navigation bar icons follow the app theme, not only the system one.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ExhaleTheme(dark = dark) { ExhaleRoot(vm, tapped = tapped, onOpened = { open.value = null }) }
        }
    }

    override fun onStart() {
        super.onStart()
        // Coming to the front sets the reminder alarms again: Android may have dropped them, or the user
        // may have just allowed notifications in its settings.
        ReminderScheduler.runAsync(application as ExhaleApp)
        // The home-screen widget only redraws on its own schedule, which Android may hold back, so it is
        // brought up to date whenever the app is opened and again when it is left (back to the home screen).
        WidgetRefresh.refreshAllAsync(application as ExhaleApp)
    }

    override fun onStop() {
        super.onStop()
        WidgetRefresh.refreshAllAsync(application as ExhaleApp)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tapped(intent)?.let { open.value = it }
    }

    /** The reminder [intent] is a tap on; none when it's Recents reopening a task a tap once started. */
    private fun tapped(intent: Intent?): ReminderKind? =
        intent?.takeIf { it.action == ACTION_OPEN && it.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 }?.let { enumOrNull<ReminderKind>(it.getStringExtra(EXTRA_OPEN)) }

    companion object {
        /** A reminder's tap: [EXTRA_OPEN] names its [ReminderKind]. */
        const val ACTION_OPEN = "app.exhale.OPEN"
        const val EXTRA_OPEN = "open"
    }
}
