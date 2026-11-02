package app.exhale.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.exhale.domain.PlanMath
import app.exhale.domain.TaperType
import app.exhale.domain.UserSettings
import app.exhale.ui.Fmt
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.ExBadge
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExIconButton
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExTextField
import app.exhale.ui.components.OptionCard
import app.exhale.ui.components.Overline
import app.exhale.ui.components.StepProgress
import app.exhale.ui.components.Stepper
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToLong

/** Steps after the welcome screen. The welcome screen is step 0 and has no progress bar. */
private const val STEPS = 4

private const val DEFAULT_GOAL_NAME = "Something nice"
private const val DEFAULT_GOAL_MINOR = 30000L

/**
 * First-run setup, one question per screen:
 * 0 Welcome · 1 Habit · 2 Cost · 3 Plan · 4 Goal (optional).
 * Nothing is saved until the last step, so backing out loses nothing.
 */
@Composable
fun OnboardingScreen(onFinish: (UserSettings) -> Unit, initialStep: Int = 0) {
    val c = Ex.colors
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var baseline by rememberSaveable { mutableIntStateOf(15) }
    var perPack by rememberSaveable { mutableStateOf("20") }
    var price by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(defaultCurrency()) }
    var allowanceOverride by rememberSaveable { mutableStateOf<Int?>(null) }
    var taper by rememberSaveable { mutableStateOf(TaperType.MINUS_ONE_WEEKLY) }
    var goalName by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }

    val perPackN = perPack.toIntOrNull()?.takeIf { it in 1..100 }
    val priceMinor = price.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 100).roundToLong() }
    val currencyOk = currency.length == 3 && runCatching { Currency.getInstance(currency) }.isSuccess
    val allowance = (allowanceOverride ?: (baseline - 2)).coerceIn(0, baseline)
    val goalMinor = if (goal.isBlank()) null else goal.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 100).roundToLong() }

    val stepValid = when (step) {
        2 -> perPackN != null && priceMinor != null && currencyOk
        4 -> goal.isBlank() || goalMinor != null
        else -> true
    }

    fun draft(withGoal: Boolean) = UserSettings(
        onboarded = true,
        baselinePerDay = baseline,
        cigsPerPack = perPackN ?: 20,
        packPriceMinor = priceMinor ?: 0,
        currencyCode = if (currencyOk) currency else "USD",
        taper = taper,
        startAllowance = allowance,
        planStart = LocalDate.now(),
        goalName = if (withGoal) goalName.ifBlank { DEFAULT_GOAL_NAME } else DEFAULT_GOAL_NAME,
        goalMinor = if (withGoal) goalMinor ?: DEFAULT_GOAL_MINOR else DEFAULT_GOAL_MINOR,
    )

    fun next() {
        if (!stepValid) return
        if (step < STEPS) step++ else onFinish(draft(withGoal = true))
    }

    BackHandler(enabled = step > 0) { step-- }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(horizontal = ExSpace.gutter, vertical = ExSpace.xs),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = ExSize.iconButton), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                ExIconButton(ExIcons.Back, "Back", { step-- })
                StepProgress(step, STEPS, Modifier.weight(1f).padding(horizontal = ExSpace.md))
                ExBadge("$step / $STEPS")
            }
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it * dir / 4 } + fadeOut())
            },
            label = "onboarding-step",
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = ExSpace.xl, bottom = ExSpace.md),
                verticalArrangement = Arrangement.spacedBy(ExSpace.md),
            ) {
                when (s) {
                    0 -> WelcomeStep()
                    1 -> HabitStep(baseline) { baseline = it; allowanceOverride = allowanceOverride?.coerceAtMost(it) }
                    2 -> CostStep(
                        perPack, { perPack = it.filter(Char::isDigit).take(3) },
                        price, { price = it },
                        currency, { currency = it.uppercase().filter(Char::isLetter).take(3) },
                        baseline, perPackN, priceMinor, if (currencyOk) currency else null,
                    )
                    3 -> PlanStep(baseline, allowance, { allowanceOverride = it }, taper, { taper = it })
                    4 -> GoalStep(
                        goalName, { goalName = it },
                        goal, { goal = it },
                        estimateFourWeeksMinor(draft(withGoal = false)),
                        if (currencyOk) currency else "USD",
                    )
                }
            }
        }

        if (step == 2 && !stepValid) {
            Text(
                "Enter the pack price. Currency is a 3-letter code like USD, EUR or KZT.",
                style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(bottom = ExSpace.sm),
            )
        }
        ExButton(
            when (step) { 0 -> "Get started"; STEPS -> "Start"; else -> "Continue" },
            ::next,
            Modifier.fillMaxWidth(),
            enabled = stepValid,
        )
        if (step == STEPS) {
            ExButton("Skip the goal for now", { onFinish(draft(withGoal = false)) }, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
        }
    }
}

@Composable
private fun WelcomeStep() {
    val c = Ex.colors
    Overline("Exhale")
    Column {
        Text("Burn less.", style = ExType.display, color = c.ink)
        Text("Exhale more.", style = ExType.display.copy(fontStyle = ExType.titleItalic.fontStyle), color = c.oxyInk)
    }
    Text(
        "Track every cigarette in one gesture, cut down at your own pace, and ride out cravings.",
        style = ExType.body, color = c.inkSoft,
    )
    Spacer(Modifier.height(ExSpace.md))
    Box(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(ExRadius.pill)
            .background(Brush.horizontalGradient(listOf(c.ember, c.flare, c.sky, c.oxy)))
    )
    Row {
        Text("just smoked", style = ExType.caption, color = c.emberInk, modifier = Modifier.weight(1f))
        Text("next one earned", style = ExType.caption, color = c.oxyInk)
    }
    Text(
        "Warm means smoke. Cool means recovery. As you cut down, the whole app cools.",
        style = ExType.body, color = c.ink,
    )
    Spacer(Modifier.height(ExSpace.lg))
    Text("Everything stays on this phone. No account, no cloud.", style = ExType.caption, color = c.inkFaint)
}


@Composable
private fun StepTitle(eyebrow: String, title: String, body: String) {
    val c = Ex.colors
    Overline(eyebrow)
    Text(title, style = ExType.title, color = c.ink)
    Text(body, style = ExType.body, color = c.inkSoft)
}

@Composable
private fun HabitStep(baseline: Int, onChange: (Int) -> Unit) {
    StepTitle(
        "Your habit",
        "How many a day, usually?",
        "Your honest average before Exhale. It's your starting point, and money saved is counted against it.",
    )
    Spacer(Modifier.height(ExSpace.lg))
    Stepper(baseline, onChange, 1..60, if (baseline == 1) "cigarette a day" else "cigarettes a day")
}

@Composable
private fun CostStep(
    perPack: String, onPerPack: (String) -> Unit,
    price: String, onPrice: (String) -> Unit,
    currency: String, onCurrency: (String) -> Unit,
    baseline: Int, perPackN: Int?, priceMinor: Long?, validCurrency: String?,
) {
    val c = Ex.colors
    StepTitle("Cost", "What does a pack cost?", "Used only to show the money you keep.")
    ExTextField("Pack price", price, onPrice, type = KeyboardType.Decimal, placeholder = "10.00")
    Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
        ExTextField("Cigarettes per pack", perPack, onPerPack, Modifier.weight(1.3f), type = KeyboardType.Number)
        ExTextField("Currency", currency, onCurrency, Modifier.weight(1f))
    }
    if (perPackN != null && priceMinor != null && validCurrency != null) {
        val perCig = priceMinor.toDouble() / perPackN
        val perMonth = (perCig * baseline * 30).toLong()
        Text(
            "That's ${Fmt.money(perCig.toLong(), validCurrency, decimals = true)} a cigarette, " +
                "about ${Fmt.money(perMonth, validCurrency)} a month at $baseline a day.",
            style = ExType.body, color = c.sunInk,
        )
    }
}

@Composable
private fun PlanStep(
    baseline: Int,
    allowance: Int,
    onAllowance: (Int) -> Unit,
    taper: TaperType,
    onTaper: (TaperType) -> Unit,
) {
    val c = Ex.colors
    StepTitle("Your plan", "Start at", "Cigarettes allowed per day this week. Two under your usual is a gentle start.")
    Stepper(allowance, onAllowance, 0..baseline, "a day this week")
    Overline("Then each week", Modifier.padding(top = ExSpace.xs))
    val today = LocalDate.now()
    TaperType.entries.forEach { t ->
        val plan = UserSettings(startAllowance = allowance, taper = t, planStart = today)
        val preview = (0..3).map { w -> PlanMath.baseAllowance(plan, today.plusDays(w * 7L)) }
        OptionCard(t.label, t.description, t == taper, { onTaper(t) }, trailing = "Weeks 1–4: " + preview.joinToString(" → "))
    }
    Text("Going over isn't failing. It's borrowed from tomorrow's allowance.", style = ExType.caption, color = c.inkSoft)
}

@Composable
private fun GoalStep(
    goalName: String, onName: (String) -> Unit,
    goal: String, onGoal: (String) -> Unit,
    fourWeeksMinor: Long,
    currency: String,
) {
    val c = Ex.colors
    StepTitle("Goal · optional", "What will the money go to?", "Something you'd like. The Money screen tracks how close you are.")
    ExTextField("Saving for", goalName, onName, placeholder = "New headphones")
    ExTextField("Amount", goal, onGoal, type = KeyboardType.Decimal, placeholder = "300")
    if (fourWeeksMinor > 0) {
        Text(
            "Following your plan, you keep about ${Fmt.money(fourWeeksMinor, currency)} in the first 4 weeks.",
            style = ExType.body, color = c.sunInk,
        )
    }
}

/** Money kept in the first 28 days if the user smokes exactly their allowance. */
private fun estimateFourWeeksMinor(s: UserSettings): Long {
    val perCig = s.pricePerCigMinor
    return (0 until 28).sumOf { d ->
        val allowed = PlanMath.baseAllowance(s, s.planStart.plusDays(d.toLong()))
        ((s.baselinePerDay - allowed).coerceAtLeast(0) * perCig)
    }.toLong()
}

private fun defaultCurrency(): String =
    runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "USD"
