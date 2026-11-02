package app.exhale.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.FloatingTabBar
import app.exhale.ui.components.TabItem
import app.exhale.ui.screens.AirScreen
import app.exhale.ui.screens.EmberScreen
import app.exhale.ui.screens.MoneyScreen
import app.exhale.ui.screens.OnboardingScreen
import app.exhale.ui.screens.RegulateScreen
import app.exhale.ui.screens.RhythmScreen
import app.exhale.ui.screens.SettingsScreen
import app.exhale.ui.screens.UiKitScreen
import app.exhale.ui.screens.UrgeWaveScreen
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExColors

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    EMBER("ember", "Ember", ExIcons.Ember),
    REGULATE("regulate", "Regulate", ExIcons.Regulate),
    RHYTHM("rhythm", "Rhythm", ExIcons.Rhythm),
    AIR("air", "Air", ExIcons.Air),
    MONEY("money", "Money", ExIcons.Money);

    fun tint(c: ExColors, heat: Float) = when (this) {
        EMBER -> c.heatInk(heat)
        REGULATE -> c.sky
        RHYTHM -> c.flare
        AIR -> c.oxy
        MONEY -> c.sun
    }
}

private const val WAVE = "wave"
private const val SETTINGS = "settings"
private const val UIKIT = "uikit"

@Composable
fun ExhaleRoot(vm: ExhaleViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val logged by vm.lastLogged.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Ex.colors.bg)) {
        when {
            !state.loaded -> Unit
            !state.settings.onboarded -> OnboardingScreen(onFinish = { vm.saveSettings(it.copy(theme = state.settings.theme)) })
            else -> MainNav(state, logged, vm)
        }
    }
}

@Composable
private fun MainNav(state: ExhaleState, logged: LoggedEvent?, vm: ExhaleViewModel) {
    val c = Ex.colors
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tab = Tab.entries.firstOrNull { it.route == entry?.destination?.route }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Tab.EMBER.route, modifier = Modifier.fillMaxSize()) {
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
                SettingsScreen(
                    current = state.settings,
                    onSave = { vm.saveSettings(it); nav.popBackStack() },
                    onTheme = vm::setTheme,
                    onDemo = { vm.loadDemo(); nav.popBackStack() },
                    onErase = vm::eraseAll,
                    onDesignSystem = { nav.navigate(UIKIT) },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(UIKIT) { UiKitScreen(state.settings.theme, vm::setTheme, onClose = { nav.popBackStack() }) }
        }
        if (tab != null) {
            FloatingTabBar(
                items = Tab.entries.map { TabItem(it.icon, it.label, it.tint(c, state.heat)) },
                selected = tab.ordinal,
                onSelect = { i ->
                    nav.navigate(Tab.entries[i].route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
