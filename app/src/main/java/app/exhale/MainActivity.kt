package app.exhale

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
import app.exhale.ui.theme.isDark
import app.exhale.ui.ExhaleRoot
import app.exhale.ui.ExhaleViewModel
import app.exhale.ui.theme.ExhaleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ExhaleViewModel = viewModel(factory = ExhaleViewModel.Factory)
            val state by vm.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = state.settings.theme.isDark(systemDark)
            // Status/navigation bar icons follow the app theme, not only the system one.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ExhaleTheme(dark = dark) { ExhaleRoot(vm) }
        }
    }
}
