package app.exhale.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.exhale.ui.screens.AirScreen
import app.exhale.ui.screens.EmberScreen
import app.exhale.ui.screens.MoneyScreen
import app.exhale.ui.screens.RegulateScreen
import app.exhale.ui.screens.RhythmScreen
import app.exhale.ui.screens.SettingsScreen
import app.exhale.ui.screens.UrgeWaveScreen
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType

private enum class Tab(val route: String, val label: String, val color: Color) {
    EMBER("ember", "Ember", Ex.Ember),
    REGULATE("regulate", "Regulate", Ex.Sky),
    RHYTHM("rhythm", "Rhythm", Ex.Flare),
    AIR("air", "Air", Ex.Oxy),
    MONEY("money", "Money", Ex.Sun),
}

private const val WAVE = "wave"
private const val SETTINGS = "settings"

@Composable
fun ExhaleRoot(vm: ExhaleViewModel = viewModel(factory = ExhaleViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val logged by vm.lastLogged.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Ex.Void)) {
        when {
            !state.loaded -> Unit
            !state.settings.onboarded -> SettingsScreen(
                current = state.settings, onboarding = true,
                onSave = vm::saveSettings, onDemo = vm::loadDemo, onErase = vm::eraseAll, onClose = null,
            )
            else -> MainNav(state, logged, vm)
        }
    }
}

@Composable
private fun MainNav(state: ExhaleState, logged: LoggedEvent?, vm: ExhaleViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }

    Column(Modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = Tab.EMBER.route,
            modifier = Modifier.weight(1f).then(if (tab != null) Modifier.statusBarsPadding() else Modifier),
        ) {
            composable(Tab.EMBER.route) {
                EmberScreen(
                    state, logged,
                    onLog = { vm.log() },
                    onUndo = vm::undo,
                    onTag = vm::tag,
                    onDismissLogged = vm::dismissLogged,
                    onUrge = { nav.navigate(WAVE) },
                    onSettings = { nav.navigate(SETTINGS) },
                )
            }
            composable(Tab.REGULATE.route) { RegulateScreen(state, onUrge = { nav.navigate(WAVE) }) }
            composable(Tab.RHYTHM.route) { RhythmScreen(state) }
            composable(Tab.AIR.route) { AirScreen(state) }
            composable(Tab.MONEY.route) { MoneyScreen(state) }
            composable(WAVE) {
                UrgeWaveScreen(
                    onFinish = { started, outcome, trigger -> vm.finishUrge(started, outcome, trigger); nav.popBackStack() },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(SETTINGS) {
                Box(Modifier.statusBarsPadding()) {
                    SettingsScreen(
                        current = state.settings, onboarding = false,
                        onSave = { vm.saveSettings(it); nav.popBackStack() },
                        onDemo = { vm.loadDemo(); nav.popBackStack() },
                        onErase = vm::eraseAll,
                        onClose = { nav.popBackStack() },
                    )
                }
            }
        }
        if (tab != null) BottomBar(tab, state.heat) { t ->
            nav.navigate(t.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, heat: Float, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ex.Void)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val sel = t == current
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .semantics { selected = sel }
                    .clickable(role = Role.Tab) { onSelect(t) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val dot = if (t == Tab.EMBER) Ex.heatColor(heat) else t.color
                Canvas(Modifier.size(if (sel) 12.dp else 6.dp)) { drawCircle(if (sel) dot else Ex.Line) }
                Text(t.label.uppercase(), style = ExType.eyebrow, color = if (sel) Ex.Ink else Ex.Mute)
            }
        }
    }
}
