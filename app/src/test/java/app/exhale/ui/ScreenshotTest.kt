package app.exhale.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import app.exhale.domain.Money
import app.exhale.domain.TodayPlan
import app.exhale.domain.Recovery
import app.exhale.domain.SmokeLog
import app.exhale.domain.Stats
import app.exhale.domain.TaperType
import app.exhale.domain.Trigger
import app.exhale.domain.TriggerEntry
import app.exhale.domain.Triggers
import app.exhale.domain.Urge
import app.exhale.domain.UrgeOutcome
import app.exhale.domain.UserSettings
import app.exhale.ui.screens.AirScreen
import app.exhale.ui.screens.EmberScreen
import app.exhale.ui.screens.HistoryScreen
import app.exhale.ui.screens.MoneyScreen
import app.exhale.ui.screens.OnboardingScreen
import app.exhale.ui.screens.RhythmScreen
import app.exhale.ui.screens.PrototypeTools
import app.exhale.ui.screens.SettingsPage
import app.exhale.ui.screens.SettingsScreen
import app.exhale.ui.components.ExToastState
import app.exhale.ui.screens.UrgeWaveScreen
import app.exhale.ui.theme.Ex
import app.exhale.ui.components.AppBarScrollBehavior
import app.exhale.ui.components.ExAppBar
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.FloatingTabBar
import app.exhale.ui.components.TabItem
import app.exhale.ui.screens.UiKitScreen
import app.exhale.ui.screens.EmberOrbCanvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import app.exhale.domain.ThemeMode
import app.exhale.domain.HomeStyle
import app.exhale.ui.screens.GlowEdge
import androidx.compose.ui.Alignment
import app.exhale.ui.theme.ExhaleTheme
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.runtime.remember
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * Renders every screen with fixed sample data on the JVM.
 * Record: ./gradlew recordRoborazziDebug  → images in app/build/outputs/roborazzi
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-02T14:21:00Z")

    private fun sampleState(minutesSinceLast: Long = 161): ExhaleState {
        val today = now.atZone(zone).toLocalDate()
        val settings = UserSettings(
            onboarded = true, baselinePerDay = 16, startAllowance = 14, taper = TaperType.MINUS_ONE_WEEKLY,
            planStart = today.minusDays(29), goalName = "New headphones", goalMinor = 59900,
        )
        val r = Random(3)
        val weights = floatArrayOf(.1f, 0f, 0f, 0f, 0f, 0f, .2f, .9f, 1.6f, .9f, .5f, .6f, .7f, 1.3f, .6f, .5f, .7f, .8f, 1.2f, 1.1f, .9f, 1f, .7f, .3f)
        val logs = mutableListOf<SmokeLog>()
        var id = 0L
        for (d in 1..29) {
            val day = today.minusDays(d.toLong())
            repeat(10 + r.nextInt(5)) {
                var x = r.nextFloat() * weights.sum(); var h = 0
                while (x > weights[h]) { x -= weights[h]; h++ }
                val trig = when (h) { in 6..9 -> Trigger.COFFEE; in 12..14 -> Trigger.AFTER_FOOD; in 15..17 -> Trigger.STRESS; else -> Trigger.DRINKS }
                logs += SmokeLog(id++, day.atTime(h, r.nextInt(60)).atZone(zone).toInstant(), trig, false)
            }
        }
        listOf(8 * 60 + 5L, 9 * 60 + 40L, 11 * 60L).forEach { logs += SmokeLog(id++, today.atStartOfDay(zone).toInstant().plusSeconds(it * 60), Trigger.COFFEE, false) }
        logs += SmokeLog(id++, now.minus(Duration.ofMinutes(minutesSinceLast)), Trigger.AFTER_FOOD, false)
        logs.sortBy { it.at }
        return build(settings, logs, emptyList())
    }

    /**
     * 60 days that follow the plan down from 16 to about 8, now and then over, so Rhythm has a "30 days before" and
     * History has two full months. Some cravings ridden out along the way.
     */
    private fun longState(): ExhaleState {
        val today = now.atZone(zone).toLocalDate()
        val settings = UserSettings(
            onboarded = true, baselinePerDay = 18, startAllowance = 16, taper = TaperType.MINUS_ONE_WEEKLY,
            planStart = today.minusDays(59), goalName = "New headphones", goalMinor = 59900,
        )
        val r = Random(5)
        val weights = floatArrayOf(.1f, 0f, 0f, 0f, 0f, 0f, .2f, .9f, 1.6f, .9f, .5f, .6f, .7f, 1.3f, .6f, .5f, .7f, .8f, 1.2f, 1.1f, .9f, 1f, .7f, .3f)
        val logs = mutableListOf<SmokeLog>()
        val urges = mutableListOf<Urge>()
        var id = 0L
        for (back in 59 downTo 1) {
            val day = today.minusDays(back.toLong())
            val i = 59 - back
            // Mornings ease off faster than evenings as the weeks go by.
            val w = weights.copyOf().also { it[8] *= 1f - i / 120f; it[7] *= 1f - i / 150f }
            repeat((16 - i / 7) + r.nextInt(-2, 2)) {
                var x = r.nextFloat() * w.sum(); var h = 0
                while (x > w[h] && h < 23) { x -= w[h]; h++ }
                val trig = when (h) { in 6..9 -> Trigger.COFFEE; in 12..14 -> Trigger.AFTER_FOOD; in 15..17 -> Trigger.STRESS; else -> Trigger.DRINKS }
                logs += SmokeLog(id++, day.atTime(h, r.nextInt(60)).atZone(zone).toInstant(), if (r.nextFloat() < 0.7f) trig else null, false)
            }
            if (r.nextFloat() < 0.35f) {
                val t = day.atTime(10 + r.nextInt(10), r.nextInt(60)).atZone(zone).toInstant()
                urges += Urge(id++, t, t.plusSeconds(240), if (r.nextFloat() < 0.8f) UrgeOutcome.SURVIVED else UrgeOutcome.SMOKED, Trigger.STRESS)
            }
        }
        listOf(8 * 60 + 5L, 9 * 60 + 40L, 11 * 60L).forEach { logs += SmokeLog(id++, today.atStartOfDay(zone).toInstant().plusSeconds(it * 60), Trigger.COFFEE, false) }
        logs.sortBy { it.at }
        return build(settings, logs, urges)
    }

    private fun build(settings: UserSettings, logs: List<SmokeLog>, urges: List<Urge>): ExhaleState {
        val plan = TodayPlan.of(settings, logs, now, zone)
        val dataStart = Stats.dataStart(settings.planStart, logs, zone)
        val money = Money.summary(settings, logs, now, zone)
        return ExhaleState(
            loaded = true, now = now, settings = settings, logs = logs, urges = urges, plan = plan,
            money = money,
            clearFraction = 0.4f,
            recovery = Recovery.summary(settings, logs, now, zone),
            dataStart = dataStart,
            week = weekDays(settings, logs, plan.date, zone, dataStart),
        )
    }

    /**
     * Renders [content] in a theme. With a [tab], the shared app bar (that tab's leading, from [bar],
     * Rhythm's with a day [picked], Ember's with [todayOpen]; with its border while [barScrolled],
     * slid away when [barHidden]) and the tab bar sit over it, as in the app.
     */
    private fun shot(
        name: String,
        dark: Boolean = true,
        tab: Int? = null,
        bar: ExhaleState? = null,
        picked: Boolean = false,
        todayOpen: Boolean = false,
        barScrolled: Boolean = false,
        barHidden: Boolean = false,
        glow: Boolean = false,
        holdMs: Long? = null,
        content: @Composable () -> Unit,
    ) {
        val barState = bar ?: sampleState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ExhaleTheme(dark = dark) {
                val c = Ex.colors
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    // The Glow home's edge light sits under the screen, as in the app.
                    if (glow) GlowEdge(barState.heat, alpha = { 1f }, hold = { 0f }, flash = { 0f })
                    content()
                    if (tab != null) {
                        val behavior = AppBarScrollBehavior().apply { if (barHidden) hiddenPx = 10_000f }
                        ExAppBar(LeadingKey(Tab.entries[tab], picked), {}, Modifier.align(Alignment.TopCenter), barScrolled, behavior, seeThrough = { if (glow) 1f else 0f }) { k ->
                            TabLeading(k, barState, todayOpen, {})
                        }
                        FloatingTabBar(
                            listOf(
                                TabItem(ExIcons.Ember, "Ember", c.heat(0.2f)), TabItem(ExIcons.Rhythm, "Rhythm", c.flare), TabItem(ExIcons.Air, "Air", c.oxy),
                                TabItem(ExIcons.Money, "Money", c.sun),
                            ),
                            tab, {}, Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        // Press and hold the Glow home off-centre for [holdMs], without letting go.
        if (holdMs != null) {
            compose.onNodeWithContentDescription("Press and hold anywhere to log a cigarette.")
                .performTouchInput { down(androidx.compose.ui.geometry.Offset(width * 0.65f, height * 0.45f)) }
            compose.mainClock.advanceTimeBy(holdMs)
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun emberDark() = shot("1_ember", true, 0) { EmberScreen(sampleState(), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun emberLight() = shot("1_ember", false, 0) { EmberScreen(sampleState(), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun emberWaitingDark() = shot("1c_ember_waiting", true, 0, bar = sampleState(38)) { EmberScreen(sampleState(38), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun emberWaitingLight() = shot("1c_ember_waiting", false, 0, bar = sampleState(38)) { EmberScreen(sampleState(38), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun rhythmDayDark() = shot("4b_rhythm_day", true, 1, picked = true) { val st = longState(); RhythmScreen(st, day = st.today.minusDays(1)) }
    @Test fun rhythmHourDark() = shot("4c_rhythm_hour", true, 1) { RhythmScreen(longState(), initialHour = 8) }
    @Test fun rhythmLinesLight() = shot("4d_rhythm_lines", false, 1) { RhythmScreen(longState(), initialLines = true) }
    @Test fun rhythmNewDark() = shot("4e_rhythm_new", true, 1) {
        val st = sampleState()
        val start = st.today.minusDays(4)
        val logs = st.logs.filter { !it.at.atZone(zone).toLocalDate().isBefore(start) }
        RhythmScreen(st.copy(logs = logs, dataStart = start, settings = st.settings.copy(planStart = start), week = weekDays(st.settings.copy(planStart = start), logs, st.today, zone, start)))
    }
    @Test fun historyDark() = shot("10_history", true) { HistoryScreen(longState(), {}, {}) }
    @Test fun historyLight() = shot("10_history", false) { HistoryScreen(longState(), {}, {}) }
    private fun overState() = sampleState(38).let { it.copy(plan = it.plan.copy(count = 12, allowance = 8, borrowed = 2)) }
    @Test fun emberOverDark() = shot("1d_ember_over", true, 0, bar = overState()) { EmberScreen(overState(), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun emberTodayDark() = shot("1e_ember_today", true, 0, bar = overState(), todayOpen = true) { EmberScreen(overState(), null, {}, {}, { _, _ -> }, {}, {}, todayOpen = true) }
    @Test fun emberTodayLight() = shot("1e_ember_today", false, 0, bar = overState(), todayOpen = true) { EmberScreen(overState(), null, {}, {}, { _, _ -> }, {}, {}, todayOpen = true) }
    /** A lowered day (base 10, 2 borrowed) that is over today's 8 but still within the base: nothing more is borrowed. */
    private fun loweredWithinState() = overState().let { it.copy(plan = it.plan.copy(count = 9)) }
    @Test fun emberTodayLoweredWithinDark() = shot("1g_ember_today_lowered", true, 0, bar = loweredWithinState(), todayOpen = true) { EmberScreen(loweredWithinState(), null, {}, {}, { _, _ -> }, {}, {}, todayOpen = true) }
    @Test fun emberTodayLoweredWithinLight() = shot("1g_ember_today_lowered", false, 0, bar = loweredWithinState(), todayOpen = true) { EmberScreen(loweredWithinState(), null, {}, {}, { _, _ -> }, {}, {}, todayOpen = true) }
    @Test fun emberTodayWithinLight() = shot("1f_ember_today_within", false, 0, bar = sampleState(38), todayOpen = true) { EmberScreen(sampleState(38), null, {}, {}, { _, _ -> }, {}, {}, todayOpen = true) }
    @Test fun emberHotDark() = shot("1b_ember_logged", true, 0, bar = sampleState(4)) { EmberScreen(sampleState(4), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {}) }
    @Test fun emberHotLight() = shot("1b_ember_logged", false, 0, bar = sampleState(4)) { EmberScreen(sampleState(4), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {}) }
    // The Glow home: the gap as edge light and a line above the button.
    private fun glowState(minutesSinceLast: Long = 161) = sampleState(minutesSinceLast).let { it.copy(settings = it.settings.copy(home = HomeStyle.GLOW)) }
    @Test fun glowDark() = shot("1h_glow", true, 0, bar = glowState(), glow = true) { EmberScreen(glowState(), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun glowLight() = shot("1h_glow", false, 0, bar = glowState(), glow = true) { EmberScreen(glowState(), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun glowWaitingDark() = shot("1i_glow_waiting", true, 0, bar = glowState(38), glow = true) { EmberScreen(glowState(38), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun glowWaitingLight() = shot("1i_glow_waiting", false, 0, bar = glowState(38), glow = true) { EmberScreen(glowState(38), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun glowLoggedDark() = shot("1j_glow_logged", true, 0, bar = glowState(4), glow = true) { EmberScreen(glowState(4), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {}) }
    // Mid-hold: the warm trail on the line and the glow gathering under the finger.
    @Test fun glowHoldingDark() = shot("1k_glow_holding", true, 0, bar = glowState(38), glow = true, holdMs = 900) { EmberScreen(glowState(38), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun glowHoldingLight() = shot("1k_glow_holding", false, 0, bar = glowState(38), glow = true, holdMs = 900) { EmberScreen(glowState(38), null, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun waveDark() = shot("3_wave", true) { UrgeWaveScreen({ _, _, _ -> }, {}, startAt = 1.5f) }
    @Test fun waveLight() = shot("3_wave", false) { UrgeWaveScreen({ _, _, _ -> }, {}, startAt = 1.5f) }
    @Test fun waveReadyDark() = shot("3b_wave_get_ready", true) { UrgeWaveScreen({ _, _, _ -> }, {}, startAt = 63.4f) }
    @Test fun waveHoldLight() = shot("3c_wave_hold", false) { UrgeWaveScreen({ _, _, _ -> }, {}, startAt = 64.6f) }
    @Test fun rhythmDark() = shot("4_rhythm", true, 1) { RhythmScreen(longState()) }
    @Test fun rhythmLight() = shot("4_rhythm", false, 1) { RhythmScreen(longState()) }
    @Test fun airDark() = shot("5_air", true, 2) { AirScreen(sampleState()) }
    @Test fun airLight() = shot("5_air", false, 2) { AirScreen(sampleState()) }
    // Scrolled down past the sky: the app bar has slid away and the hero has gone up with the cards; the sky has
    // risen a little and dimmed behind them.
    @Test fun airScrolledDark() = shot("5b_air_scrolled", true, 2, barHidden = true) { AirScreen(sampleState(), initialScroll = 2000) }
    @Test fun airScrolledLight() = shot("5b_air_scrolled", false, 2, barHidden = true) { AirScreen(sampleState(), initialScroll = 2000) }
    // Partway, after scrolling back up a little: the app bar is back with its border; the hero stays in place
    // while the next card comes up to meet it.
    @Test fun airPinnedDark() = shot("5f_air_pinned", true, 2, barScrolled = true) { AirScreen(sampleState(), initialScroll = 800) }
    @Test fun airPinnedLight() = shot("5f_air_pinned", false, 2, barScrolled = true) { AirScreen(sampleState(), initialScroll = 800) }
    // Four minutes after a cigarette: the gap starts again, but milestones reached before stay lit.
    @Test fun airLoggedDark() = shot("5c_air_logged", true, 2) { AirScreen(sampleState(4)) }
    @Test fun airLoggedLight() = shot("5c_air_logged", false, 2) { AirScreen(sampleState(4)) }
    // The whole screen at once: sky, next milestone, today's gaps, timeline and the cutting-down card.
    @Config(qualifiers = "w411dp-h2000dp-xxhdpi")
    @Test fun airFullDark() = shot("5e_air_full", true) { AirScreen(sampleState()) }
    @Config(qualifiers = "w411dp-h2000dp-xxhdpi")
    @Test fun airFullLight() = shot("5e_air_full", false) { AirScreen(sampleState()) }
    @Test fun airNewDark() = shot("5d_air_new", true, 2) {
        val st = sampleState()
        AirScreen(st.copy(logs = emptyList(), plan = TodayPlan.of(st.settings, emptyList(), now, zone), recovery = Recovery.summary(st.settings, emptyList(), now, zone)))
    }
    @Test fun moneyDark() = shot("6_money", true, 3) { MoneyScreen(sampleState()) }
    @Test fun moneyLight() = shot("6_money", false, 3) { MoneyScreen(sampleState()) }
    @Test fun moneyBigKztDark() = shot("6b_money_kzt", true, 3) {
        // A pack of 20 at 19,900 KZT: six-figure amounts must still fit on one line.
        val st = sampleState()
        val s = st.settings.copy(currencyCode = "KZT", packPriceMinor = 1_990_000, goalMinor = 25_000_000)
        MoneyScreen(st.copy(settings = s, money = Money.summary(s, st.logs, now, zone)))
    }
    @Test fun moneyLongGoalDark() = shot("6c_money_long", true, 3, bar = longState()) {
        // 60 days in (packs, both recent amounts, pace) and a goal name too long for two lines.
        val st = longState()
        val s = st.settings.copy(goalName = "A weekend trip to the mountains with family", goalMinor = 105_000)
        MoneyScreen(st.copy(settings = s, money = Money.summary(s, st.logs, now, zone)))
    }
    // Settings as in a release build (no prototype tools), with the user's own triggers.
    @Composable private fun Settings(
        page: SettingsPage? = null,
        allowance: Int? = null,
        scroll: Int = 0,
        tools: Boolean = false,
        toast: String? = null,
        triggerKey: String? = null,
    ) {
        val st = sampleState()
        SettingsScreen(
            st.settings, st.today, {}, {}, {}, {},
            prototypeTools = if (tools) PrototypeTools({}, {}) else null,
            initialScroll = scroll,
            initialPage = page,
            initialAllowance = allowance ?: st.settings.startAllowance,
            toast = remember { ExToastState(toast) },
            triggers = ownTriggers,
            triggerUses = mapOf("COFFEE" to 52, "DRIVING" to 12, PHONE.key to 37),
            initialTriggerKey = triggerKey,
        )
    }

    // A list someone has shaped: two of their own, Phone calls moved up, Driving hidden.
    private val PHONE = Trigger("custom:phone", "Phone calls")
    private val ownTriggers: List<TriggerEntry> = Triggers.defaults
        .let { Triggers.add(it, PHONE.label, PHONE.key)!!.list }
        .let { Triggers.add(it, "Bus stop", "custom:bus")!!.list }
        .let { Triggers.move(it, 8, 1) }
        .let { Triggers.setHidden(it, "DRIVING", true) }

    // Twelve shown, so the Logged card tucks three behind "3 more".
    private fun manyTriggersState(minutesSinceLast: Long = 4): ExhaleState {
        var list = ownTriggers
        listOf("Waiting", "Work break", "Gaming").forEachIndexed { i, n -> list = Triggers.add(list, n, "custom:$i")!!.list }
        return sampleState(minutesSinceLast).copy(triggers = list)
    }

    @Test fun settingsTriggersDark() = shot("7h_settings_triggers", true) { Settings(page = SettingsPage.TRIGGERS) }
    @Test fun settingsTriggersLight() = shot("7h_settings_triggers", false) { Settings(page = SettingsPage.TRIGGERS) }
    @Test fun settingsTriggerOwnDark() = shot("7i_settings_trigger", true) { Settings(page = SettingsPage.TRIGGER, triggerKey = "custom:bus") }
    @Test fun settingsTriggerOwnLight() = shot("7i_settings_trigger", false) { Settings(page = SettingsPage.TRIGGER, triggerKey = "custom:bus") }
    @Test fun settingsTriggerBuiltInDark() = shot("7j_settings_trigger_builtin", true) { Settings(page = SettingsPage.TRIGGER, triggerKey = "COFFEE") }
    @Test fun settingsTriggerBuiltInLight() = shot("7j_settings_trigger_builtin", false) { Settings(page = SettingsPage.TRIGGER, triggerKey = "COFFEE") }
    @Test fun settingsTriggerNewDark() = shot("7k_settings_trigger_new", true) { Settings(page = SettingsPage.TRIGGER) }
    @Test fun settingsTriggerNewLight() = shot("7k_settings_trigger_new", false) { Settings(page = SettingsPage.TRIGGER) }
    @Test fun emberLoggedMoreDark() = shot("1l_ember_logged_more", true, 0, bar = manyTriggersState()) {
        EmberScreen(manyTriggersState(), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {})
    }
    @Test fun emberLoggedMoreLight() = shot("1l_ember_logged_more", false, 0, bar = manyTriggersState()) {
        EmberScreen(manyTriggersState(), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {})
    }
    @Test fun emberYourOwnDark() = shot("1m_ember_your_own", true, 0, bar = sampleState(4)) {
        EmberScreen(sampleState(4), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {}, initialDraft = "Phone calls")
    }
    @Test fun emberYourOwnLight() = shot("1m_ember_your_own", false, 0, bar = sampleState(4)) {
        EmberScreen(sampleState(4), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {}, initialDraft = "Phone calls")
    }
    @Test fun waveOwnDark() = shot("3d_wave_own", true) {
        UrgeWaveScreen({ _, _, _ -> }, {}, startAt = 30f, triggers = Triggers.shown(ownTriggers))
    }
    // The dialog has its own window, so these capture the whole screen.
    private fun waveNaming(dark: Boolean) {
        compose.setContent {
            ExhaleTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Ex.colors.bg)) {
                    UrgeWaveScreen({ _, _, _ -> }, {}, startAt = 30f, triggers = Triggers.shown(ownTriggers), naming = true)
                }
            }
        }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/3e_wave_naming_${if (dark) "dark" else "light"}.png")
    }
    @Test fun waveNamingDark() = waveNaming(true)
    @Test fun waveNamingLight() = waveNaming(false)
    @Test fun waveOwnLight() = shot("3d_wave_own", false) {
        UrgeWaveScreen({ _, _, _ -> }, {}, startAt = 30f, triggers = Triggers.shown(ownTriggers))
    }
    @Test fun settingsDark() = shot("7_settings", true) { Settings() }
    @Test fun settingsLight() = shot("7_settings", false) { Settings() }
    // Scrolled, as a debug build: the header stays pinned with its border; Prototype tools at the bottom.
    @Test fun settingsScrolledDark() = shot("7b_settings_scrolled", true) { Settings(scroll = 900, tools = true) }
    @Test fun settingsScrolledLight() = shot("7b_settings_scrolled", false) { Settings(scroll = 900, tools = true) }
    // A count changed and not saved yet: the value in sky, "Save changes" up from the bottom.
    @Test fun settingsCountedDark() = shot("7c_settings_counted", true) { Settings(allowance = 12) }
    @Test fun settingsCountedLight() = shot("7c_settings_counted", false) { Settings(allowance = 12) }
    @Test fun settingsTaperDark() = shot("7d_settings_taper", true) { Settings(page = SettingsPage.TAPER) }
    @Test fun settingsTaperLight() = shot("7d_settings_taper", false) { Settings(page = SettingsPage.TAPER) }
    @Test fun settingsPackDark() = shot("7e_settings_pack", true) { Settings(page = SettingsPage.PACK) }
    @Test fun settingsPackLight() = shot("7e_settings_pack", false) { Settings(page = SettingsPage.PACK) }
    @Test fun settingsGoalDark() = shot("7f_settings_goal", true) { Settings(page = SettingsPage.GOAL) }
    @Test fun settingsGoalLight() = shot("7f_settings_goal", false) { Settings(page = SettingsPage.GOAL) }
    // Back from A pack with the price left empty: the rest saved, the price kept.
    @Test fun settingsSavedDark() = shot("7g_settings_saved", true) { Settings(toast = "Saved. Pack price stays 10.00 $.") }

    @Test fun onboardingWelcomeDark() = shot("0a_onboarding_welcome", true) { OnboardingScreen({}, initialStep = 0) }
    @Test fun onboardingWelcomeLight() = shot("0a_onboarding_welcome", false) { OnboardingScreen({}, initialStep = 0) }
    @Test fun onboardingHabitDark() = shot("0b_onboarding_habit", true) { OnboardingScreen({}, initialStep = 1) }
    @Test fun onboardingCostDark() = shot("0c_onboarding_cost", true) { OnboardingScreen({}, initialStep = 2) }
    @Test fun onboardingPlanDark() = shot("0d_onboarding_plan", true) { OnboardingScreen({}, initialStep = 3) }
    @Test fun onboardingPlanLight() = shot("0d_onboarding_plan", false) { OnboardingScreen({}, initialStep = 3) }
    @Test fun onboardingGoalDark() = shot("0e_onboarding_goal", true) { OnboardingScreen({}, initialStep = 4) }

    @Composable
    private fun OrbMoments() {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            androidx.compose.foundation.layout.Row {
                EmberOrbCanvas(heat = 0.05f, hold = 0f, burst = 0f, breathe = 0.6f, phase = 3f, modifier = Modifier.size(200.dp), ring = 1f)
                EmberOrbCanvas(heat = 0.95f, hold = 0f, burst = 0f, breathe = 0.2f, phase = 9f, modifier = Modifier.size(200.dp), ring = 0.05f)
            }
            androidx.compose.foundation.layout.Row {
                EmberOrbCanvas(heat = 0.1f, hold = 0.6f, burst = 0f, breathe = 0f, phase = 5f, modifier = Modifier.size(200.dp), ring = 0.4f)
                EmberOrbCanvas(heat = 0.9f, hold = 0.3f, burst = 0.35f, breathe = 0f, phase = 7f, modifier = Modifier.size(200.dp), ring = 0f)
            }
        }
    }

    @Test fun orbDark() = shot("9_ember_orb", true) { OrbMoments() }
    @Test fun orbLight() = shot("9_ember_orb", false) { OrbMoments() }

    @Config(qualifiers = "w411dp-h4600dp-xxhdpi")
    @Test fun uiKitDark() = shot("8_uikit", true) { UiKitScreen(ThemeMode.DARK, {}, {}) }

    @Config(qualifiers = "w411dp-h4600dp-xxhdpi")
    @Test fun uiKitLight() = shot("8_uikit", false) { UiKitScreen(ThemeMode.LIGHT, {}, {}) }
}
