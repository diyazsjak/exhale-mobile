package app.exhale.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.exhale.domain.PlanMath
import app.exhale.domain.SettingsInput
import app.exhale.domain.SettingsInput.Field
import app.exhale.domain.TaperType
import app.exhale.domain.HomeStyle
import app.exhale.domain.ThemeMode
import app.exhale.domain.UserSettings
import app.exhale.ui.Fmt
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.CounterRow
import app.exhale.ui.components.DialogAction
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExChip
import app.exhale.ui.components.ExChoiceDialog
import app.exhale.ui.components.ExIconButton
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.ExTextField
import app.exhale.ui.components.ExToast
import app.exhale.ui.components.ExToastState
import app.exhale.ui.components.FieldNote
import app.exhale.ui.components.HeaderLabel
import app.exhale.ui.components.ListRow
import app.exhale.ui.components.OptionCard
import app.exhale.ui.components.Overline
import app.exhale.ui.components.ScreenTitle
import app.exhale.ui.components.SegmentedControl
import app.exhale.ui.components.rememberToastState
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** The edit screens Settings opens. Each one saves when you go back. */
enum class SettingsPage { TAPER, PACK, GOAL }

/** Rows that exist only in debug builds: the live component catalogue and demo data. */
class PrototypeTools(val onDesignSystem: () -> Unit, val onDemo: () -> Unit)

private enum class Ask { UNSAVED, ERASE, DEMO }

/** Room the "Save changes" bar takes at the bottom: 28 fade + 60 button + 20. */
private val SaveBarHeight = 108.dp

/**
 * Appearance and Home screen apply at once. Before Exhale and Starting allowance are counted here and wait for
 * "Save changes", which rises from the bottom while either differs from what's saved; Back with
 * unsaved counts asks first. How it drops, A pack and Saving for open their own screens, which
 * save when you go back and say so in a toast. Keeps the original plan start.
 * [prototypeTools] is null in release builds.
 */
@Composable
fun SettingsScreen(
    current: UserSettings,
    today: LocalDate,
    onSave: (UserSettings) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onErase: () -> Unit,
    onClose: () -> Unit,
    prototypeTools: PrototypeTools? = null,
    onHome: (HomeStyle) -> Unit = {},
    initialScroll: Int = 0,
    initialPage: SettingsPage? = null,
    initialAllowance: Int = current.startAllowance,
    toast: ExToastState = rememberToastState(),
) {
    var baseline by rememberSaveable { mutableIntStateOf(current.baselinePerDay) }
    var allowance by rememberSaveable { mutableIntStateOf(initialAllowance) }
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var ask by rememberSaveable { mutableStateOf<Ask?>(null) }
    val mainScroll = rememberScrollState(initialScroll)
    val baselineChanged = baseline != current.baselinePerDay
    val allowanceChanged = allowance != current.startAllowance
    val dirty = baselineChanged || allowanceChanged
    val saveCounts = { onSave(current.copy(baselinePerDay = baseline, startAllowance = allowance)) }

    // Going back from an edit screen: save what's valid, say what was kept.
    val pageDone: (SettingsInput.Applied) -> Unit = done@{ a ->
        if (page == null) return@done
        if (a.changed) onSave(a.settings)
        val kept = a.kept.map { keptNote(it, a.settings) }
        val note = (listOfNotNull("Saved".takeIf { a.changed }) + kept).joinToString(". ")
        if (note.isNotEmpty()) toast.show(if (kept.isEmpty()) note else "$note.")
        page = null
    }

    BackHandler(enabled = page == null && dirty) { ask = Ask.UNSAVED }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            page,
            transitionSpec = {
                val spec = tween<androidx.compose.ui.unit.IntOffset>(260, easing = FastOutSlowInEasing)
                val fade = tween<Float>(260, easing = FastOutSlowInEasing)
                if (targetState != null) {
                    (slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 3 } + fadeOut(fade))
                        .apply { targetContentZIndex = 1f }
                } else {
                    (slideInHorizontally(spec) { -it / 3 } + fadeIn(fade) togetherWith slideOutHorizontally(spec) { it })
                        .apply { targetContentZIndex = 0f }
                }
            },
            label = "settings-page",
        ) { p ->
            when (p) {
                null -> SettingsMain(
                    current, today, baseline, { baseline = it }, baselineChanged, allowance, { allowance = it }, allowanceChanged,
                    dirty, mainScroll, onTheme, onHome, { page = it }, { ask = Ask.ERASE }, prototypeTools?.let { t -> PrototypeTools(t.onDesignSystem) { ask = Ask.DEMO } },
                    onBack = { if (dirty) ask = Ask.UNSAVED else onClose() },
                    onSaveCounts = { saveCounts(); toast.show("Saved") },
                )
                SettingsPage.TAPER -> TaperPage(current, allowance, allowanceChanged, pageDone)
                SettingsPage.PACK -> PackPage(current, pageDone)
                SettingsPage.GOAL -> GoalPage(current, pageDone)
            }
        }
        ExToast(toast, Modifier.align(Alignment.BottomCenter), bottom = if (dirty && page == null) SaveBarHeight else ExSpace.xl)
    }

    when (ask) {
        Ask.UNSAVED -> ExChoiceDialog(
            "Save your changes?",
            "You changed " + listOfNotNull(
                "your count before Exhale".takeIf { baselineChanged },
                "the starting allowance".takeIf { allowanceChanged },
            ).joinToString(" and ") + ".",
            listOf(
                DialogAction("Save changes", ButtonStyle.Primary) { saveCounts(); ask = null; onClose() },
                DialogAction("Discard", ButtonStyle.Tonal) { ask = null; onClose() },
                DialogAction("Keep editing", ButtonStyle.Ghost) { ask = null },
            ),
            onDismiss = { ask = null },
        )
        Ask.ERASE -> ExChoiceDialog(
            "Erase everything?",
            "Your logs, cravings, plan and goal leave this phone for good. Exhale starts again from the welcome screen.",
            listOf(
                DialogAction("Erase everything", ButtonStyle.Primary) { ask = null; onErase() },
                DialogAction("Keep my data", ButtonStyle.Ghost) { ask = null },
            ),
            onDismiss = { ask = null },
        )
        Ask.DEMO -> ExChoiceDialog(
            "Replace your data?",
            "Demo data swaps your logs and settings for 60 days of made-up history. Your own data can't be brought back.",
            listOf(
                DialogAction("Load demo data", ButtonStyle.Primary) { ask = null; prototypeTools?.onDemo?.invoke() },
                DialogAction("Cancel", ButtonStyle.Ghost) { ask = null },
            ),
            onDismiss = { ask = null },
        )
        null -> Unit
    }
}

@Composable
private fun SettingsMain(
    current: UserSettings,
    today: LocalDate,
    baseline: Int,
    onBaseline: (Int) -> Unit,
    baselineChanged: Boolean,
    allowance: Int,
    onAllowance: (Int) -> Unit,
    allowanceChanged: Boolean,
    dirty: Boolean,
    scroll: ScrollState,
    onTheme: (ThemeMode) -> Unit,
    onHome: (HomeStyle) -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onErase: () -> Unit,
    tools: PrototypeTools?,
    onBack: () -> Unit,
    onSaveCounts: () -> Unit,
) {
    val c = Ex.colors
    val since = current.planStart.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
    Box(Modifier.fillMaxSize()) {
        ExScreen(
            bottomClearance = if (dirty) SaveBarHeight else ExSpace.xl,
            scrollState = scroll,
            header = {
                ExIconButton(ExIcons.Back, "Back", onBack)
                HeaderLabel("Settings")
                Spacer(Modifier.size(ExSize.iconButton))
            },
        ) {
            Overline("Appearance")
            SegmentedControl(ThemeMode.entries.map { it.label }, current.theme.ordinal, { onTheme(ThemeMode.entries[it]) })

            Overline("Home screen", Modifier.paddingTop())
            SegmentedControl(HomeStyle.entries.map { it.label }, current.home.ordinal, { onHome(HomeStyle.entries[it]) })
            Text(
                if (current.home == HomeStyle.GLOW) "The gap as light around the screen, and a line that fills toward the next one."
                else "The ember you hold, with a ring that fills toward the next one.",
                style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(horizontal = 4.dp),
            )

            Overline("Plan", Modifier.paddingTop())
            ExCard(padding = ExSpace.sm) {
                CounterRow("Before Exhale", baseline, onBaseline, 1..SettingsInput.COUNT_MAX, caption = "cigarettes a day", changed = baselineChanged)
                CounterRow("Starting allowance", allowance, onAllowance, 0..SettingsInput.COUNT_MAX, caption = "a day, since $since", changed = allowanceChanged)
                ListRow("How it drops", { onOpen(SettingsPage.TAPER) }, value = current.taper.label)
            }
            PlanLine(current.copy(startAllowance = allowance), today, baseline)

            Overline("Money", Modifier.paddingTop())
            ExCard(padding = ExSpace.sm) {
                ListRow("A pack", { onOpen(SettingsPage.PACK) }, value = "${current.cigsPerPack} for ${Fmt.money(current.packPriceMinor, current.currencyCode, decimals = true)}")
                ListRow("Saving for", { onOpen(SettingsPage.GOAL) }, value = Fmt.money(current.goalMinor, current.currencyCode), sub = current.goalName)
            }

            Overline("Your data", Modifier.paddingTop())
            ExCard(padding = ExSpace.sm) { ListRow("Erase all data", onErase) }
            Text("Exhale keeps everything on this phone. No account, no cloud.", style = ExType.caption, color = c.inkFaint, modifier = Modifier.padding(horizontal = 4.dp))

            if (tools != null) {
                Overline("Prototype tools", Modifier.paddingTop())
                ExCard(padding = ExSpace.sm) {
                    ListRow("Design system", tools.onDesignSystem)
                    ListRow("Load demo data", tools.onDemo, value = "60 days")
                }
                Text("Shown in debug builds only.", style = ExType.caption, color = c.inkFaint, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        AnimatedVisibility(
            dirty,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to c.bg.copy(alpha = 0f), 0.3f to c.bg))
                    .navigationBarsPadding()
                    .padding(horizontal = ExSpace.gutter)
                    .padding(top = 28.dp, bottom = 20.dp),
            ) {
                ExButton("Save changes", onSaveCounts, Modifier.fillMaxWidth())
            }
        }
    }
}

/** "This week 11 a day, next week 10." for the plan as counted on screen, saved or not. */
@Composable
private fun PlanLine(plan: UserSettings, today: LocalDate, baseline: Int) {
    val c = Ex.colors
    val now = PlanMath.baseAllowance(plan, today)
    val next = PlanMath.baseAllowance(plan, today.plusWeeks(1))
    val line = buildAnnotatedString {
        val num = SpanStyle(color = c.ink)
        if (plan.taper == TaperType.HOLD) {
            append("Holding at "); withStyle(num) { append("$now") }; append(" a day.")
        } else {
            append("This week "); withStyle(num) { append("$now") }; append(" a day, next week "); withStyle(num) { append("$next") }; append(".")
        }
        if (plan.startAllowance > baseline) append(" That start is above your usual $baseline.")
    }
    Text(line, style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(horizontal = 4.dp))
}

/** An edit screen: back saves (see [SettingsInput]); a faint line says so under the content. */
@Composable
private fun EditPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Ex.colors
    BackHandler(onBack = onBack)
    ExScreen(
        bottomClearance = ExSpace.xl,
        header = {
            ExIconButton(ExIcons.Back, "Back, saving changes", onBack)
            HeaderLabel(title)
            Spacer(Modifier.size(ExSize.iconButton))
        },
    ) {
        content()
        Text("Changes save when you go back.", style = ExType.caption, color = c.inkFaint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun TaperPage(current: UserSettings, allowance: Int, allowanceUnsaved: Boolean, onDone: (SettingsInput.Applied) -> Unit) {
    var taper by rememberSaveable { mutableStateOf(current.taper) }
    val since = current.planStart.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
    EditPage("How it drops", { onDone(SettingsInput.Applied(current.copy(taper = taper), taper != current.taper, emptyList())) }) {
        ScreenTitle(
            "How fast should it drop?",
            subtitle = "From $allowance a day, since $since." + if (allowanceUnsaved) " That new start isn't saved yet." else "",
        )
        TaperType.entries.forEach { t ->
            OptionCard(t.label, t.description, t == taper, { taper = t }, trailing = "Weeks 1–4: " + PlanMath.firstWeeks(allowance, t).joinToString(" → "))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackPage(current: UserSettings, onDone: (SettingsInput.Applied) -> Unit) {
    val quick = remember { quickCurrencies() }
    var perPack by rememberSaveable { mutableStateOf(current.cigsPerPack.toString()) }
    var price by rememberSaveable { mutableStateOf(SettingsInput.priceText(current.packPriceMinor)) }
    var currency by rememberSaveable { mutableStateOf(current.currencyCode) }
    var other by rememberSaveable { mutableStateOf(current.currencyCode !in quick) }
    val c = Ex.colors
    EditPage("A pack", { onDone(SettingsInput.applyPack(current, perPack, price, currency)) }) {
        ScreenTitle("What does a pack cost?", subtitle = "Money saved is worked out from this.")
        Column(verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
                ExTextField(
                    "Per pack", perPack, { perPack = SettingsInput.digits(it) }, Modifier.weight(0.75f),
                    type = KeyboardType.Number, placeholder = current.cigsPerPack.toString(), suffix = "cigs",
                )
                ExTextField(
                    "Pack price", price, { price = SettingsInput.money(it) }, Modifier.weight(1.25f),
                    type = KeyboardType.Decimal, placeholder = SettingsInput.priceText(current.packPriceMinor),
                    suffix = currency.takeIf(SettingsInput::currencyOk) ?: current.currencyCode,
                )
            }
            FieldNote(packNote(perPack, price, current))
        }
        Column(verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
            Text("Currency", style = ExType.caption, color = c.inkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ExSpace.xs), verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
                quick.forEach { code -> ExChip(code, !other && currency == code, { other = false; currency = code }) }
                ExChip("Other", other, { other = true; if (currency in quick) currency = "" })
            }
        }
        if (other) {
            Column(verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
                ExTextField("Currency code", currency, { currency = SettingsInput.currency(it) }, placeholder = "CHF", suffix = "3 letters")
                FieldNote(currencyNote(currency, current))
            }
        }
    }
}

@Composable
private fun GoalPage(current: UserSettings, onDone: (SettingsInput.Applied) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current.goalName) }
    var amount by rememberSaveable { mutableStateOf(SettingsInput.amountText(current.goalMinor)) }
    val c = Ex.colors
    EditPage("Saving for", { onDone(SettingsInput.applyGoal(current, name, amount)) }) {
        ScreenTitle("What are you saving for?", subtitle = "The Money tab fills a bar toward it.")
        Column(verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            ExTextField("Saving for", name, { name = it }, placeholder = SettingsInput.GOAL_NAME_BLANK, maxLength = SettingsInput.GOAL_NAME_MAX)
            ExTextField(
                "Amount", amount, { amount = SettingsInput.money(it) },
                type = KeyboardType.Decimal, placeholder = SettingsInput.amountText(current.goalMinor), suffix = current.currencyCode,
            )
            FieldNote(if (SettingsInput.minor(amount) == null) "Enter an amount, like 599. Going back keeps ${Fmt.money(current.goalMinor, current.currencyCode)}." else "")
            if (name.isBlank()) {
                Text("Left empty, it's called “${SettingsInput.GOAL_NAME_BLANK}”.", style = ExType.caption, color = c.inkSoft, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

private fun packNote(perPack: String, price: String, s: UserSettings): String = listOfNotNull(
    when {
        perPack.isEmpty() -> "Enter how many come in a pack. Going back keeps ${s.cigsPerPack}."
        SettingsInput.packSize(perPack) == null -> "A pack holds 1 to ${SettingsInput.COUNT_MAX}. Going back keeps ${s.cigsPerPack}."
        else -> null
    },
    if (SettingsInput.minor(price) == null) "Enter the pack price, like 10.00. Going back keeps ${Fmt.money(s.packPriceMinor, s.currencyCode, decimals = true)}." else null,
).joinToString(" ")

private fun currencyNote(code: String, s: UserSettings): String = when {
    code.length < 3 -> "A currency code has 3 letters, like CHF. Going back keeps ${s.currencyCode}."
    !SettingsInput.currencyOk(code) -> "That isn't a currency code we know. Going back keeps ${s.currencyCode}."
    else -> ""
}

/** What the toast says about a field whose saved value stayed; [s] is what was saved. */
private fun keptNote(f: Field, s: UserSettings): String = when (f) {
    Field.PACK_SIZE -> "Pack size stays ${s.cigsPerPack}"
    Field.PACK_PRICE -> "Pack price stays ${Fmt.money(s.packPriceMinor, s.currencyCode, decimals = true)}"
    Field.CURRENCY -> "Currency stays ${s.currencyCode}"
    Field.GOAL_AMOUNT -> "Amount stays ${Fmt.money(s.goalMinor, s.currencyCode)}"
}

/** Three quick currency chips: the phone region's first, then USD, EUR and KZT. */
private fun quickCurrencies(): List<String> {
    val region = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
    return (listOfNotNull(region) + listOf("USD", "EUR", "KZT")).distinct().take(3)
}

private fun Modifier.paddingTop() = this.padding(top = ExSpace.xs)
