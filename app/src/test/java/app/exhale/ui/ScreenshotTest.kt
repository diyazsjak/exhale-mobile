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
        )
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ExhaleTheme { Box(Modifier.fillMaxSize().background(Ex.Void)) { content() } } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun ember() = shot("1_ember") { EmberScreen(sampleState(), null, {}, {}, { _, _ -> }, {}, {}, {}) }
    @Test fun emberHot() = shot("1b_ember_hot") { EmberScreen(sampleState(4), LoggedEvent(1, 5, 12), {}, {}, { _, _ -> }, {}, {}, {}) }
    @Test fun regulate() = shot("2_regulate") { RegulateScreen(sampleState(41)) {} }
    @Test fun wave() = shot("3_wave") { UrgeWaveScreen({ _, _, _ -> }, {}) }
    @Test fun rhythm() = shot("4_rhythm") { RhythmScreen(sampleState()) }
    @Test fun air() = shot("5_air") { AirScreen(sampleState()) }
    @Test fun money() = shot("6_money") { MoneyScreen(sampleState()) }
    @Test fun onboardingWelcome() = shot("0a_onboarding_welcome") { OnboardingScreen({}, initialStep = 0) }
    @Test fun onboardingHabit() = shot("0b_onboarding_habit") { OnboardingScreen({}, initialStep = 1) }
    @Test fun onboardingCost() = shot("0c_onboarding_cost") { OnboardingScreen({}, initialStep = 2) }
    @Test fun onboardingPlan() = shot("0d_onboarding_plan") { OnboardingScreen({}, initialStep = 3) }
    @Test fun onboardingGoal() = shot("0e_onboarding_goal") { OnboardingScreen({}, initialStep = 4) }
    @Test fun settings() = shot("7_settings") { SettingsScreen(sampleState().settings, {}, {}, {}, {}) }
}
