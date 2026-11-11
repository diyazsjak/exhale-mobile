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
import app.exhale.notify.ReminderScheduler
import app.exhale.ui.ExhaleRoot
import app.exhale.ui.ExhaleViewModel
import app.exhale.ui.OpenTarget
import app.exhale.ui.theme.ExhaleTheme
import app.exhale.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A screen a tapped reminder asked for, until the app has gone there. */
    private val open = MutableStateFlow<OpenTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only for a fresh start: after a rotation the intent has already been followed.
        if (savedInstanceState == null) open.value = targetOf(intent)
        // Opening Exhale sets the reminder alarms again, in case Android dropped them.
        ReminderScheduler.runAsync(application as ExhaleApp)
        setContent {
            val vm: ExhaleViewModel = viewModel(factory = ExhaleViewModel.Factory)
            val state by vm.state.collectAsStateWithLifecycle()
            val target by open.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = state.settings.theme.isDark(systemDark)
            // Status/navigation bar icons follow the app theme, not only the system one.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ExhaleTheme(dark = dark) { ExhaleRoot(vm, open = target, onOpened = { open.value = null }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetOf(intent)?.let { open.value = it }
    }

    private fun targetOf(intent: Intent?): OpenTarget? =
        intent?.takeIf { it.action == ACTION_OPEN }?.getStringExtra(EXTRA_OPEN)?.let { name -> OpenTarget.entries.firstOrNull { it.name == name } }

    companion object {
        /** A reminder's tap: [EXTRA_OPEN] names the [OpenTarget]. */
        const val ACTION_OPEN = "app.exhale.OPEN"
        const val EXTRA_OPEN = "open"
    }
}
