package app.exhale.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.exhale.domain.PlanMath
import app.exhale.domain.SmokeLog
import app.exhale.domain.Stats
import app.exhale.domain.TaperType
import app.exhale.domain.Trigger
import app.exhale.domain.UserSettings
import app.exhale.ui.screens.AirScreen
import app.exhale.ui.screens.EmberScreen
import app.exhale.ui.screens.MoneyScreen
import app.exhale.ui.screens.OnboardingScreen
import app.exhale.ui.screens.RegulateScreen
import app.exhale.ui.screens.RhythmScreen
import app.exhale.ui.screens.SettingsScreen
import app.exhale.ui.screens.UrgeWaveScreen
import app.exhale.ui.theme.Ex
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.FloatingTabBar
import app.exhale.ui.components.TabItem
import app.exhale.ui.screens.UiKitScreen
import app.exhale.domain.ThemeMode
import androidx.compose.ui.Alignment
import app.exhale.ui.theme.ExhaleTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
        val todayCount = Stats.countOn(logs, today, zone)
        val y = Stats.countOn(logs, today.minusDays(1), zone)
        val allowance = PlanMath.allowance(settings, today, y)
        val interval = PlanMath.intervalMinutes(allowance, settings.wakingMinutes)
        val last = logs.last()
        val start = settings.planStart.atStartOfDay(zone).toInstant()
        val notSmoked = Stats.notSmoked(settings.baselinePerDay, start, now, logs.count { it.at >= start })
        return ExhaleState(
            loaded = true, now = now, settings = settings, logs = logs, today = today,
            todayCount = todayCount, allowance = allowance, intervalMinutes = interval,
            lastLog = last, sinceLast = Duration.between(last.at, now),
            nextAllowedAt = last.at.plus(Duration.ofMinutes(interval.toLong())),
            heat = Stats.heat(now, last.at, interval),
            notSmoked = notSmoked, savedMinor = Stats.moneySavedMinor(notSmoked, settings.pricePerCigMinor),
            longestGapWeek = Stats.longestGap(logs, now, Duration.ofDays(7)),
            clearFraction = 0.4f,
            week = weekDays(settings, logs, today, zone),
        )
    }

    private fun shot(name: String, dark: Boolean = true, tab: Int? = null, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ExhaleTheme(dark = dark) {
                val c = Ex.colors
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    content()
                    if (tab != null) {
                        FloatingTabBar(
                            listOf(
                                TabItem(ExIcons.Ember, "Ember", c.heat(0.2f)), TabItem(ExIcons.Regulate, "Regulate", c.sky),
                                TabItem(ExIcons.Rhythm, "Rhythm", c.flare), TabItem(ExIcons.Air, "Air", c.oxy),
                                TabItem(ExIcons.Money, "Money", c.sun),
                            ),
                            tab, {}, Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun emberDark() = shot("1_ember", true, 0) { EmberScreen(sampleState(), null, {}, {}, { _, _ -> }, {}, {}, {}) }
    @Test fun emberLight() = shot("1_ember", false, 0) { EmberScreen(sampleState(), null, {}, {}, { _, _ -> }, {}, {}, {}) }
    @Test fun emberHotDark() = shot("1b_ember_logged", true, 0) { EmberScreen(sampleState(4), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {}, {}) }
    @Test fun emberHotLight() = shot("1b_ember_logged", false, 0) { EmberScreen(sampleState(4), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {}, {}) }
    @Test fun regulateDark() = shot("2_regulate", true, 1) { RegulateScreen(sampleState(41)) {} }
    @Test fun regulateLight() = shot("2_regulate", false, 1) { RegulateScreen(sampleState(41)) {} }
    @Test fun waveDark() = shot("3_wave", true) { UrgeWaveScreen({ _, _, _ -> }, {}) }
    @Test fun waveLight() = shot("3_wave", false) { UrgeWaveScreen({ _, _, _ -> }, {}) }
    @Test fun rhythmDark() = shot("4_rhythm", true, 2) { RhythmScreen(sampleState()) }
    @Test fun rhythmLight() = shot("4_rhythm", false, 2) { RhythmScreen(sampleState()) }
    @Test fun airDark() = shot("5_air", true, 3) { AirScreen(sampleState()) }
    @Test fun airLight() = shot("5_air", false, 3) { AirScreen(sampleState()) }
    @Test fun moneyDark() = shot("6_money", true, 4) { MoneyScreen(sampleState()) }
    @Test fun moneyLight() = shot("6_money", false, 4) { MoneyScreen(sampleState()) }
    @Test fun settingsDark() = shot("7_settings", true) { SettingsScreen(sampleState().settings, {}, {}, {}, {}, {}, {}) }
    @Test fun settingsLight() = shot("7_settings", false) { SettingsScreen(sampleState().settings, {}, {}, {}, {}, {}, {}) }

    @Test fun onboardingWelcomeDark() = shot("0a_onboarding_welcome", true) { OnboardingScreen({}, initialStep = 0) }
    @Test fun onboardingWelcomeLight() = shot("0a_onboarding_welcome", false) { OnboardingScreen({}, initialStep = 0) }
    @Test fun onboardingHabitDark() = shot("0b_onboarding_habit", true) { OnboardingScreen({}, initialStep = 1) }
    @Test fun onboardingCostDark() = shot("0c_onboarding_cost", true) { OnboardingScreen({}, initialStep = 2) }
    @Test fun onboardingPlanDark() = shot("0d_onboarding_plan", true) { OnboardingScreen({}, initialStep = 3) }
    @Test fun onboardingPlanLight() = shot("0d_onboarding_plan", false) { OnboardingScreen({}, initialStep = 3) }
    @Test fun onboardingGoalDark() = shot("0e_onboarding_goal", true) { OnboardingScreen({}, initialStep = 4) }

    @Config(qualifiers = "w411dp-h4600dp-xxhdpi")
    @Test fun uiKitDark() = shot("8_uikit", true) { UiKitScreen(ThemeMode.DARK, {}, {}) }

    @Config(qualifiers = "w411dp-h4600dp-xxhdpi")
    @Test fun uiKitLight() = shot("8_uikit", false) { UiKitScreen(ThemeMode.LIGHT, {}, {}) }
}
