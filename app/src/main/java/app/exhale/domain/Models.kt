package app.exhale.domain

import java.time.Instant
import java.time.LocalDate

/** The triggers Exhale starts with. They can be hidden, reordered or (while unused) deleted, never renamed. */
enum class BuiltInTrigger(val label: String) {
    COFFEE("Coffee"),
    STRESS("Stress"),
    AFTER_FOOD("After food"),
    DRINKS("Drinks"),
    DRIVING("Driving"),
    BORED("Bored"),
    SOCIAL("Social"),
    OTHER("Other"),
}

/**
 * Why the user lit up. Optional, asked after the log. Built in, or named by the user ([Triggers]).
 * [key] is what logs store: a built-in's name ("COFFEE"), or [Triggers.CUSTOM_PREFIX] + a random id.
 */
data class Trigger(val key: String, val label: String) {
    val builtIn: BuiltInTrigger? get() = BuiltInTrigger.entries.firstOrNull { it.name == key }

    /** The label inside a sentence: built-ins in lower case ("Mostly coffee"), the user's own as typed. */
    val inSentence: String get() = if (builtIn != null) label.lowercase() else label

    companion object {
        fun of(b: BuiltInTrigger) = Trigger(b.name, b.label)

        /** The built-in whose key is [key], with its own label; null for the user's own keys. */
        fun builtInOf(key: String): Trigger? = BuiltInTrigger.entries.firstOrNull { it.name == key }?.let(::of)

        val COFFEE = of(BuiltInTrigger.COFFEE)
        val STRESS = of(BuiltInTrigger.STRESS)
        val AFTER_FOOD = of(BuiltInTrigger.AFTER_FOOD)
        val DRINKS = of(BuiltInTrigger.DRINKS)
        val DRIVING = of(BuiltInTrigger.DRIVING)
        val BORED = of(BuiltInTrigger.BORED)
        val SOCIAL = of(BuiltInTrigger.SOCIAL)
        val OTHER = of(BuiltInTrigger.OTHER)

        /** All built-ins in their starting order. */
        val BUILT_IN: List<Trigger> = BuiltInTrigger.entries.map(::of)
    }
}

/** How the daily allowance shrinks over time. */
enum class TaperType(val label: String, val description: String) {
    MINUS_ONE_WEEKLY("−1 per week", "Allowance drops by one cigarette every week."),
    TEN_PERCENT_WEEKLY("−10% per week", "Allowance drops by about a tenth every week."),
    HOLD("Hold steady", "Keep the starting allowance. Good for the first week of tracking."),
}

enum class UrgeOutcome { SURVIVED, SMOKED }

/** App appearance. SYSTEM follows the phone's dark/light setting. */
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** What the home tab shows: the Ember orb, or the gap as light around the screen (Glow). */
enum class HomeStyle(val label: String) { EMBER("Ember"), GLOW("Glow") }

data class SmokeLog(
    val id: Long,
    val at: Instant,
    val trigger: Trigger?,
    val fromUrge: Boolean,
)

data class Urge(
    val id: Long,
    val startedAt: Instant,
    val endedAt: Instant,
    val outcome: UrgeOutcome,
    val trigger: Trigger?,
)

data class UserSettings(
    val onboarded: Boolean = false,
    /** Cigarettes per day before using the app. Drives money and the starting allowance. */
    val baselinePerDay: Int = 15,
    val cigsPerPack: Int = 20,
    /** Price per pack in minor units (cents). */
    val packPriceMinor: Long = 1000,
    val currencyCode: String = "USD",
    val taper: TaperType = TaperType.MINUS_ONE_WEEKLY,
    /** Allowance on plan day 0. Defaults to baseline − 2 at onboarding. */
    val startAllowance: Int = 13,
    val planStart: LocalDate = LocalDate.now(),
    /** Waking minutes the allowance is spread across when computing the interval. */
    val wakingMinutes: Int = 16 * 60,
    val goalName: String = SettingsInput.GOAL_NAME_BLANK,
    val goalMinor: Long = 30000,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val home: HomeStyle = HomeStyle.EMBER,
    /** Epoch day on which the user opened the "borrowed from yesterday" note; hides the badge dot. */
    val borrowNoticeSeenDay: Long = -1,
) {
    val pricePerCigMinor: Double get() = packPriceMinor.toDouble() / cigsPerPack.coerceAtLeast(1)

    /** Whether the user has opened the "borrowed from yesterday" note on [day]. */
    fun borrowNoticeSeenOn(day: LocalDate): Boolean = borrowNoticeSeenDay == day.toEpochDay()

    /** These settings once the user has opened the borrowed note on [day]. */
    fun withBorrowNoticeSeen(day: LocalDate): UserSettings = copy(borrowNoticeSeenDay = day.toEpochDay())
}
