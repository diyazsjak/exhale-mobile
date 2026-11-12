package app.exhale.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.exhale.domain.Export
import app.exhale.domain.Import
import app.exhale.domain.HomeStyle
import app.exhale.BuildConfig
import app.exhale.domain.ReminderKind
import app.exhale.domain.ReminderOutlook
import app.exhale.domain.UrgeOutcome
import app.exhale.notify.ReminderNotifier
import app.exhale.ui.components.ExAppBar
import app.exhale.ui.components.ExToastState
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.FloatingTabBar
import app.exhale.ui.components.Overline
import app.exhale.ui.components.TabItem
import app.exhale.ui.components.rememberAppBarScrollBehavior
import app.exhale.ui.components.rememberToastState
import app.exhale.ui.screens.AirScreen
import app.exhale.ui.screens.DataControls
import app.exhale.ui.screens.EmberScreen
import app.exhale.ui.screens.GlowEdge
import app.exhale.ui.screens.HistoryScreen
import app.exhale.ui.screens.MoneyScreen
import app.exhale.ui.screens.OnboardingScreen
import app.exhale.ui.screens.PrototypeTools
import app.exhale.ui.screens.ReminderControls
import app.exhale.ui.screens.RhythmScreen
import app.exhale.ui.screens.SettingsScreen
import app.exhale.ui.screens.TriggerActions
import app.exhale.ui.screens.TodayBadge
import app.exhale.ui.screens.UiKitScreen
import app.exhale.ui.screens.UrgeWaveScreen
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

internal enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    EMBER("ember", "Ember", ExIcons.Ember),
    RHYTHM("rhythm", "Rhythm", ExIcons.Rhythm),
    AIR("air", "Air", ExIcons.Air),
    MONEY("money", "Money", ExIcons.Money);

    fun tint(c: ExColors, heat: Float) = when (this) {
        EMBER -> c.heatInk(heat)
        RHYTHM -> c.flare
        AIR -> c.oxy
        MONEY -> c.sun
    }
}

/** What the app bar's leading shows: one per tab, and Rhythm's changes while a day is [picked]. */
internal data class LeadingKey(val tab: Tab, val picked: Boolean = false)

/**
 * Each tab's leading in the shared app bar. Ember: today's count, which opens the Today popover
 * ([todayOpen]). Rhythm: what it covers ("Rhythm" alone while a day is picked: the day is named in
 * its filter bar). Air and Money: what the screen is about.
 */
@Composable
internal fun RowScope.TabLeading(key: LeadingKey, state: ExhaleState, todayOpen: Boolean, onToday: () -> Unit) {
    when (key.tab) {
        Tab.EMBER -> TodayBadge(state, expanded = todayOpen, onClick = onToday)
        Tab.RHYTHM -> Overline(if (key.picked) "Rhythm" else "Rhythm · Last 30 days")
        Tab.AIR -> Overline("Recovery · if you stop now")
        Tab.MONEY -> Overline("Money · since ${Fmt.dayMonth(state.settings.planStart)}")
    }
}

/** Screen transitions: short and sliding. */
private const val NAV_MS = 260
private fun <T> navSpec() = tween<T>(NAV_MS, easing = FastOutSlowInEasing)

private fun tabIndex(route: String?) = Tab.entries.indexOfFirst { it.route == route }

private fun isTabSwitch(from: String?, to: String?) = tabIndex(from) >= 0 && tabIndex(to) >= 0

/** 1 when moving to a tab further right, -1 to one further left. */
private fun tabDirection(from: String?, to: String?) = if (tabIndex(to) > tabIndex(from)) 1 else -1

/**
 * Tabs slide like pages, full width, in tab order. A full-screen route (Urge Wave, Settings, History)
 * pushes in from the right over a screen that recedes a third and fades; popping reverses that.
 */
private fun slideIn(from: String?, to: String?, forward: Boolean): EnterTransition = when {
    isTabSwitch(from, to) -> slideInHorizontally(navSpec()) { w -> tabDirection(from, to) * w }
    forward -> slideInHorizontally(navSpec()) { w -> w }
    else -> slideInHorizontally(navSpec()) { w -> -w / 3 } + fadeIn(navSpec())
}

private fun slideOut(from: String?, to: String?, forward: Boolean): ExitTransition = when {
    isTabSwitch(from, to) -> slideOutHorizontally(navSpec()) { w -> -tabDirection(from, to) * w }
    forward -> slideOutHorizontally(navSpec()) { w -> -w / 3 } + fadeOut(navSpec())
    else -> slideOutHorizontally(navSpec()) { w -> w }
}

private const val WAVE = "wave"
private const val SETTINGS = "settings"
private const val UIKIT = "uikit"
private const val HISTORY = "history"

/** [tapped]: a reminder the user tapped; [onOpened] once its screen is open (or can't be, before onboarding). */
@Composable
fun ExhaleRoot(vm: ExhaleViewModel, tapped: ReminderKind?, onOpened: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val logged by vm.lastLogged.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Ex.colors.bg)) {
        when {
            !state.loaded -> Unit
            !state.settings.onboarded -> {
                // Reminders are only sent once onboarded; one tapped after an erase has nowhere to go.
                LaunchedEffect(tapped) { if (tapped != null) onOpened() }
                OnboardingScreen(onFinish = { vm.saveSettings(it.copy(theme = state.settings.theme)) })
            }
            else -> MainNav(state, logged, vm, tapped, onOpened)
        }
    }
}

@Composable
private fun MainNav(state: ExhaleState, logged: LoggedEvent?, vm: ExhaleViewModel, tapped: ReminderKind?, onOpened: () -> Unit) {
    val c = Ex.colors
    val nav = rememberNavController()
    val triggerActions = remember(vm) {
        TriggerActions({ vm.addTrigger(it) }, vm::renameTrigger, vm::setTriggerHidden, vm::moveTrigger, vm::deleteTrigger)
    }
    // Switches tab as the tab bar does, keeping each tab's state.
    val goToTab = { t: Tab ->
        nav.navigate(t.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val tab = Tab.entries.firstOrNull { it.route == entry?.destination?.route }
    // A tapped reminder: a busy-hour nudge opens Urge Wave over where the user was (unless it's open
    // already), the others switch to their tab.
    LaunchedEffect(tapped) {
        when (tapped) {
            ReminderKind.BUSY_HOUR -> if (nav.currentDestination?.route != WAVE) nav.navigate(WAVE)
            ReminderKind.MILESTONE -> goToTab(Tab.AIR)
            ReminderKind.GOAL -> goToTab(Tab.MONEY)
            ReminderKind.PLAN_WEEK -> goToTab(Tab.EMBER)
            null -> return@LaunchedEffect
        }
        onOpened()
    }
    // The day Rhythm is showing. Hoisted so History can open a day there.
    var rhythmDay by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    // Ember's Today popover opens from the badge in the shared app bar. It closes when leaving Ember.
    var todayOpen by rememberSaveable { mutableStateOf(false) }
    // The tabs' scroll positions, hoisted so the app bar knows when content is under it.
    val rhythmScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val airScroll = rememberScrollState()
    val moneyScroll = rememberScrollState()
    val currentTab by rememberUpdatedState(tab)
    val scrolledUnder by remember {
        derivedStateOf {
            when (currentTab) {
                Tab.RHYTHM -> rhythmScroll.value > 0
                Tab.AIR -> airScroll.value > 0
                Tab.MONEY -> moneyScroll.value > 0
                else -> false
            }
        }
    }
    // The app bar hides as a tab scrolls up and comes back as it scrolls down; each tab starts with it shown.
    val barScroll = rememberAppBarScrollBehavior()
    // On Air the bar stays with the held hero and leaves with it; Air reports how far it is held.
    var airHeld by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    LaunchedEffect(tab) {
        if (tab != Tab.EMBER) todayOpen = false
        if (tab == Tab.AIR) barScroll.holdWhile({ airScroll.value.toFloat() }, { airHeld }) else barScroll.clearHold()
        barScroll.show()
    }
    // The bars belong to the tab screens: when a full-screen route pushes in, they recede with the
    // tab screen (a third to the left, fading) and are covered by the incoming screen's edge, and
    // they come back the same way on pop. The last tab is kept so they have something to show.
    var lastTab by remember { mutableStateOf(Tab.EMBER) }
    SideEffect { if (tab != null) lastTab = tab }
    val barTab = tab ?: lastTab
    val away by animateFloatAsState(if (tab == null) 1f else 0f, navSpec(), label = "bars-away")

    // The Glow home's edge light: drawn once under all the tabs so it stays fixed while pages slide.
    // It fades with the tab change (quickly on the way out, over the slide on the way in) and with
    // the bars when a full-screen route pushes in. The app bar turns see-through as it shows.
    val glowHome = state.settings.home == HomeStyle.GLOW
    val onEmber by animateFloatAsState(
        if (barTab == Tab.EMBER) 1f else 0f,
        if (barTab == Tab.EMBER) navSpec() else tween(NAV_MS * 2 / 5),
        label = "glow-on-ember",
    )
    val glowAlpha = if (glowHome) onEmber * (1f - away) else 0f
    val glowHeat by animateFloatAsState(state.heat, tween(1200), label = "glow-heat")
    val glowHold = remember { mutableFloatStateOf(0f) }
    val glowFlash = remember { mutableFloatStateOf(0f) }
    val currentGlowAlpha by rememberUpdatedState(glowAlpha)

    Box(Modifier.fillMaxSize()) {
        if (glowAlpha > 0f) {
            GlowEdge(glowHeat, alpha = { currentGlowAlpha }, hold = { glowHold.floatValue }, flash = { glowFlash.floatValue })
        }
        NavHost(
            nav,
            startDestination = Tab.EMBER.route,
            modifier = Modifier.fillMaxSize().nestedScroll(barScroll),
            enterTransition = { slideIn(initialState.destination.route, targetState.destination.route, forward = true) },
            exitTransition = { slideOut(initialState.destination.route, targetState.destination.route, forward = true) },
            popEnterTransition = { slideIn(initialState.destination.route, targetState.destination.route, forward = false) },
            popExitTransition = { slideOut(initialState.destination.route, targetState.destination.route, forward = false) },
        ) {
            composable(Tab.EMBER.route) {
                EmberScreen(
                    state, logged,
                    onLog = { vm.log() },
                    onUndo = vm::undo,
                    onTag = vm::tag,
                    onDismissLogged = vm::dismissLogged,
                    onUrge = { nav.navigate(WAVE) },
                    todayOpen = todayOpen,
                    onTodayOpen = { todayOpen = it },
                    onGlowLight = { hold, flash -> glowHold.floatValue = hold; glowFlash.floatValue = flash },
                    onAddAndTag = vm::addAndTag,
                    onToday = {
                        rhythmDay = state.today
                        // Today's list is near the top, where the clock was: start there, not where Rhythm was left.
                        scope.launch { rhythmScroll.scrollTo(0) }
                        goToTab(Tab.RHYTHM)
                    },
                )
            }
            composable(Tab.RHYTHM.route) {
                RhythmScreen(
                    state,
                    scrollState = rhythmScroll,
                    day = rhythmDay,
                    onDay = { rhythmDay = it },
                    onHistory = { nav.navigate(HISTORY) },
                    onUrge = { nav.navigate(WAVE) },
                    onEditLog = vm::editLog,
                    onDeleteLog = vm::deleteLog,
                    onAddLog = vm::addLog,
                    onTagUrge = vm::tagUrge,
                    onDeleteUrge = vm::deleteUrge,
                    onAddTrigger = vm::addTrigger,
                )
            }
            composable(Tab.AIR.route) { AirScreen(state, scroll = airScroll, onHeld = { airHeld = it }) }
            composable(Tab.MONEY.route) { MoneyScreen(state, scrollState = moneyScroll) }
            composable(WAVE) {
                UrgeWaveScreen(
                    onFinish = { started, outcome, trigger ->
                        vm.finishUrge(started, outcome, trigger)
                        if (outcome == UrgeOutcome.SMOKED) {
                            // Back to the ember: the user logs it themselves by holding it.
                            if (!nav.popBackStack(Tab.EMBER.route, inclusive = false)) nav.navigate(Tab.EMBER.route)
                        } else {
                            nav.popBackStack()
                        }
                    },
                    onClose = { nav.popBackStack() },
                    triggers = state.shownTriggers,
                    onAddTrigger = vm::addTrigger,
                )
            }
            composable(SETTINGS) {
                val settingsToast = rememberToastState()
                SettingsScreen(
                    reminders = reminderControls(vm, state.reminderOutlook),
                    data = dataControls(vm, settingsToast, state.today),
                    toast = settingsToast,
                    current = state.settings,
                    today = state.today,
                    onSave = vm::saveSettings,
                    onTheme = vm::setTheme,
                    onHome = vm::setHome,
                    onErase = vm::eraseAll,
                    onClose = { nav.popBackStack() },
                    triggers = state.triggers,
                    triggerUses = state.triggerUses,
                    triggerActions = triggerActions,
                    prototypeTools = if (BuildConfig.DEBUG) {
                        PrototypeTools(onDesignSystem = { nav.navigate(UIKIT) }, onDemo = { vm.loadDemo(); nav.popBackStack() })
                    } else {
                        null
                    },
                )
            }
            composable(HISTORY) {
                HistoryScreen(
                    state,
                    onPickDay = { d -> rhythmDay = d; nav.popBackStack() },
                    onClose = { nav.popBackStack() },
                )
            }
            // The component catalogue is a prototype tool: debug builds only.
            if (BuildConfig.DEBUG) composable(UIKIT) { UiKitScreen(state.settings.theme, vm::setTheme, onClose = { nav.popBackStack() }) }
        }
        if (tab != null || away < 1f) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Clip at the incoming screen's left edge (it slides in over the same 260ms), then recede.
                    .drawWithContent { clipRect(right = size.width * (1f - away)) { this@drawWithContent.drawContent() } }
                    .graphicsLayer {
                        translationX = -away * size.width / 3f
                        alpha = 1f - away
                    }
            ) {
                ExAppBar(
                    key = LeadingKey(barTab, picked = barTab == Tab.RHYTHM && rhythmDay != null),
                    onSettings = { nav.navigate(SETTINGS) },
                    modifier = Modifier.align(Alignment.TopCenter),
                    scrolled = scrolledUnder,
                    scrollBehavior = barScroll,
                    seeThrough = { currentGlowAlpha },
                ) { k ->
                    TabLeading(k, state, todayOpen, onToday = {
                        todayOpen = !todayOpen
                        if (todayOpen && state.borrowNoticeDue) vm.markBorrowNoticeSeen()
                    })
                }
                FloatingTabBar(
                    items = Tab.entries.map { TabItem(it.icon, it.label, it.tint(c, state.heat)) },
                    selected = barTab.ordinal,
                    onSelect = { i -> goToTab(Tab.entries[i]) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Settings → Reminders' switches, with Android's side of it: turning one on when Exhale may not
 * post notifications asks for the permission (Android 13+) and turns it on only once granted. When
 * Android won't ask (refused before, or notifications blocked in its settings), Settings says so and
 * links there. Whether Exhale may post is checked again each time the screen resumes.
 */
@Composable
private fun reminderControls(vm: ExhaleViewModel, outlook: ReminderOutlook): ReminderControls {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ReminderNotifier.allowed(context)) }
    var refused by rememberSaveable { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf<ReminderKind?>(null) }
    LifecycleResumeEffect(Unit) {
        allowed = ReminderNotifier.allowed(context)
        if (allowed) refused = false
        onPauseOrDispose {}
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = ReminderNotifier.allowed(context)
        val kind = asking
        asking = null
        if (granted && kind != null) vm.setReminder(kind, true) else refused = true
    }
    // Remembered, so Settings can skip the ViewModel's once-a-second tick.
    return remember(allowed, refused, outlook) {
        ReminderControls(
            allowed = allowed,
            refused = refused,
            outlook = outlook,
            onChange = { kind, on ->
                when {
                    !on || allowed -> vm.setReminder(kind, on)
                    ReminderNotifier.canAsk(context) -> { asking = kind; ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    else -> refused = true
                }
            },
            onOpenSettings = { context.startActivity(ReminderNotifier.settingsIntent(context)) },
        )
    }
}

/**
 * Settings → Your data, with Android's side of it: the save dialog for an export and the file picker for an
 * import (the Storage Access Framework: no permission, the file goes or comes only where the user picks;
 * the picker takes any file, since providers label CSV files differently). What was saved or added is
 * reported in [toast]; anything to ask comes from [ExhaleViewModel.dataAsk].
 */
@Composable
private fun dataControls(vm: ExhaleViewModel, toast: ExToastState, today: LocalDate): DataControls {
    val context = LocalContext.current
    // One id per Settings visit, kept across rotation: what an earlier visit left to ask goes.
    val visit = rememberSaveable { UUID.randomUUID().toString() }
    remember(visit) { vm.startDataVisit(visit) }
    val ask by vm.dataAsk.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Export.MIME)) { uri ->
        if (uri != null) vm.exportCsv({ context.contentResolver.openOutputStream(uri, "w") }) { toast.show(Export.savedNote(it)) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readImport { context.contentResolver.openInputStream(uri) }
    }
    return remember(ask, today) {
        DataControls(
            onExport = { save.launch(Export.fileName(today)) },
            onImport = { pick.launch(arrayOf("*/*")) },
            ask = ask,
            onConfirmImport = { vm.confirmImport { counts -> Import.addedNote(counts)?.let(toast::show) } },
            onDismiss = vm::dismissData,
        )
    }
}
