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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.exhale.domain.PlanMath
import app.exhale.domain.ReminderKind
import app.exhale.domain.SettingsInput
import app.exhale.domain.SettingsInput.Field
import app.exhale.domain.TaperType
import app.exhale.domain.Trigger
import app.exhale.domain.TriggerEntry
import app.exhale.domain.Triggers
import app.exhale.domain.HomeStyle
import app.exhale.domain.ThemeMode
import app.exhale.domain.UserSettings
import app.exhale.ui.Fmt
import app.exhale.ui.components.BackHeader
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.CounterRow
import app.exhale.ui.components.DialogAction
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExChip
import app.exhale.ui.components.ExChoiceDialog
import app.exhale.ui.components.ExLink
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.ExTextField
import app.exhale.ui.components.ExToast
import app.exhale.ui.components.ExToastState
import app.exhale.ui.components.FieldNote
import app.exhale.ui.components.Footnote
import app.exhale.ui.components.ListRow
import app.exhale.ui.components.OptionCard
import app.exhale.ui.components.Overline
import app.exhale.ui.components.ScreenTitle
import app.exhale.ui.components.SegmentedControl
import app.exhale.ui.components.SwitchRow
import app.exhale.ui.components.rememberToastState
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.LocalDate

/** The edit screens Settings opens. Each one saves when you go back. */
enum class SettingsPage { TAPER, PACK, GOAL, TRIGGERS, TRIGGER;

    /** How deep the page sits: Settings is 0, Triggers 1, one trigger 2. Going deeper slides in from the right. */
    val depth: Int get() = if (this == TRIGGER) 2 else 1
}

/**
 * Settings → Reminders. [allowed]: Android lets Exhale post notifications. [refused]: the user just
 * tried to turn one on and Android said no, or wouldn't ask. [onChange] turns a reminder on or off
 * (asking for the permission first when needed); [onOpenSettings] opens Android's notification
 * settings for Exhale.
 */
class ReminderControls(
    val allowed: Boolean = true,
    val refused: Boolean = false,
    val onChange: (ReminderKind, Boolean) -> Unit = { _, _ -> },
    val onOpenSettings: () -> Unit = {},
)

/** Rows that exist only in debug builds: the live component catalogue and demo data. */
class PrototypeTools(val onDesignSystem: () -> Unit, val onDemo: () -> Unit)

private enum class Ask { UNSAVED, ERASE, DEMO }

/** Room the "Save changes" bar takes at the bottom: 28 fade + 60 button + 20. */
private val SaveBarHeight = 108.dp

/**
 * Appearance and Home screen apply at once. Before Exhale and Starting allowance are counted here and wait for
 * "Save changes", which rises from the bottom while either differs from what's saved; Back with
 * unsaved counts asks first. How it drops, A pack and Saving for open their own screens, which
 * save when you go back and say so in a toast. Triggers opens a list that applies changes at once.
 * Keeps the original plan start.
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
    triggers: List<TriggerEntry> = Triggers.defaults,
    triggerUses: Map<String, Int> = emptyMap(),
    triggerActions: TriggerActions = TriggerActions(),
    /** With [initialPage] TRIGGER: the trigger to open, null to add one. */
    initialTriggerKey: String? = null,
    reminders: ReminderControls = ReminderControls(),
    /** The busy hours the nudges come before; null until there's a week to go on. */
    busyHours: List<Int>? = null,
    /** What's kept already covers the goal. */
    goalReached: Boolean = false,
) {
    var baseline by rememberSaveable { mutableIntStateOf(current.baselinePerDay) }
    var allowance by rememberSaveable { mutableIntStateOf(initialAllowance) }
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var triggerKey by rememberSaveable { mutableStateOf(initialTriggerKey) }
    var triggerVisit by rememberSaveable { mutableIntStateOf(0) }
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
                val spec = tween<IntOffset>(260, easing = FastOutSlowInEasing)
                val fade = tween<Float>(260, easing = FastOutSlowInEasing)
                if ((targetState?.depth ?: 0) > (initialState?.depth ?: 0)) {
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
                    dirty, mainScroll, onTheme, onHome, { page = it }, Triggers.shown(triggers), reminders, busyHours, goalReached,
                    { ask = Ask.ERASE }, prototypeTools?.let { t -> PrototypeTools(t.onDesignSystem) { ask = Ask.DEMO } },
                    onBack = { if (dirty) ask = Ask.UNSAVED else onClose() },
                    onSaveCounts = { saveCounts(); toast.show("Saved") },
                )
                SettingsPage.TAPER -> TaperPage(current, allowance, allowanceChanged, pageDone)
                SettingsPage.PACK -> PackPage(current, pageDone)
                SettingsPage.GOAL -> GoalPage(current, pageDone)
                SettingsPage.TRIGGERS -> TriggersPage(
                    triggers, triggerUses, triggerActions,
                    onOpen = { k -> triggerKey = k; triggerVisit++; page = SettingsPage.TRIGGER },
                    onBack = { page = null },
                )
                // A fresh page on every visit: going back and straight in again (while the last one is still
                // sliding out) would otherwise reuse its state, frozen as it left.
                SettingsPage.TRIGGER -> key(triggerVisit) {
                    TriggerPage(triggers, triggerKey, triggerUses, triggerActions, toast, onBack = { page = SettingsPage.TRIGGERS })
                }
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
    shownTriggers: List<Trigger>,
    reminders: ReminderControls,
    busyHours: List<Int>?,
    goalReached: Boolean,
    onErase: () -> Unit,
    tools: PrototypeTools?,
    onBack: () -> Unit,
    onSaveCounts: () -> Unit,
) {
    val c = Ex.colors
    Box(Modifier.fillMaxSize()) {
        ExScreen(
            bottomClearance = if (dirty) SaveBarHeight else ExSpace.xl,
            scrollState = scroll,
            header = { BackHeader("Settings", onBack) },
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
            CardWithCaption {
                ExCard(padding = ExSpace.sm) {
                    CounterRow("Before Exhale", baseline, onBaseline, 1..SettingsInput.COUNT_MAX, caption = "cigarettes a day", changed = baselineChanged)
                    CounterRow("Starting allowance", allowance, onAllowance, 0..SettingsInput.COUNT_MAX, caption = "a day, since ${Fmt.dayMonth(current.planStart)}", changed = allowanceChanged)
                    ListRow("How it drops", { onOpen(SettingsPage.TAPER) }, value = current.taper.label)
                }
                PlanLine(current.copy(startAllowance = allowance), today, baseline)
            }

            Overline("Triggers", Modifier.paddingTop())
            ExCard(padding = ExSpace.sm) {
                ListRow(
                    "Your triggers", { onOpen(SettingsPage.TRIGGERS) },
                    value = "${shownTriggers.size} shown",
                    sub = shownTriggers.joinToString(", ") { it.label }.ifEmpty { "None shown" },
                )
            }

            Overline("Reminders", Modifier.paddingTop())
            Reminders(current, today, reminders, busyHours, goalReached)

            Overline("Money", Modifier.paddingTop())
            ExCard(padding = ExSpace.sm) {
                ListRow("A pack", { onOpen(SettingsPage.PACK) }, value = "${current.cigsPerPack} for ${Fmt.money(current.packPriceMinor, current.currencyCode, decimals = true)}")
                ListRow("Saving for", { onOpen(SettingsPage.GOAL) }, value = Fmt.money(current.goalMinor, current.currencyCode), sub = current.goalName)
            }

            Overline("Your data", Modifier.paddingTop())
            CardWithCaption {
                ExCard(padding = ExSpace.sm) { ListRow("Erase all data", onErase) }
                Footnote("Exhale keeps everything on this phone. No account, no cloud.")
            }

            if (tools != null) {
                Overline("Prototype tools", Modifier.paddingTop())
                CardWithCaption {
                    ExCard(padding = ExSpace.sm) {
                        ListRow("Design system", tools.onDesignSystem)
                        ListRow("Load demo data", tools.onDemo, value = "60 days")
                    }
                    Footnote("Shown in debug builds only.")
                }
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

/** A card and the caption under it, held close so the caption reads as part of the card. */
@Composable
private fun CardWithCaption(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/**
 * The four reminders as switches, each saying when it comes. A switch shows on only while the
 * reminder is on and Android lets Exhale post; when it doesn't, a line under the card says so and
 * links to Android's settings.
 */
@Composable
private fun Reminders(current: UserSettings, today: LocalDate, controls: ReminderControls, busyHours: List<Int>?, goalReached: Boolean) {
    val c = Ex.colors
    val h24 = DateFormat.is24HourFormat(LocalContext.current)
    fun on(kind: ReminderKind) = controls.allowed && kind in current.reminders
    CardWithCaption {
        ExCard(padding = ExSpace.sm) {
            SwitchRow(
                "Before your busy hours", on(ReminderKind.BUSY_HOUR), { controls.onChange(ReminderKind.BUSY_HOUR, it) },
                sub = when {
                    busyHours == null -> "15 minutes before the hours you smoke most. Starts once Exhale has a week of your days."
                    busyHours.isEmpty() -> "15 minutes before the hours you smoke most. Starts once you've logged some."
                    else -> "15 minutes before " + busyHours.sorted().joinToString(" and ") { Fmt.hour(it, h24) } + ", the hours you smoke most."
                },
            )
            SwitchRow(
                "Recovery milestones", on(ReminderKind.MILESTONE), { controls.onChange(ReminderKind.MILESTONE, it) },
                sub = "The first time you reach each one on Air, from 12 hours on.",
            )
            SwitchRow(
                "Savings goal", on(ReminderKind.GOAL), { controls.onChange(ReminderKind.GOAL, it) },
                sub = "When what you've kept covers ${current.goalName}." +
                    if (goalReached) " It already does, so a bigger goal would be next." else "",
            )
            SwitchRow(
                "New plan weeks", on(ReminderKind.PLAN_WEEK), { controls.onChange(ReminderKind.PLAN_WEEK, it) },
                sub = when {
                    current.taper == TaperType.HOLD -> "When a new week lowers your allowance. Yours holds steady, so none will come."
                    PlanMath.baseAllowance(current, today) == 0 -> "When a new week lowers your allowance. Yours is at zero, so none will come."
                    else -> "When a new week lowers your allowance."
                },
            )
        }
        if (!controls.allowed && (controls.refused || current.reminders.isNotEmpty())) {
            Footnote("Android isn't letting Exhale send notifications, so none will come.", color = c.inkSoft)
            ExLink("Open Android settings", controls.onOpenSettings)
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
    Footnote(line, color = c.inkSoft)
}

/**
 * A Settings page with a back header. When it [savesOnBack] (see [SettingsInput]), the back button
 * says so and a faint line under the content does too.
 */
@Composable
internal fun EditPage(title: String, onBack: () -> Unit, savesOnBack: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    ExScreen(
        bottomClearance = ExSpace.xl,
        header = { BackHeader(title, onBack, backLabel = if (savesOnBack) "Back, saving changes" else "Back") },
    ) {
        content()
        if (savesOnBack) Footnote("Changes save when you go back.")
    }
}

@Composable
private fun TaperPage(current: UserSettings, allowance: Int, allowanceUnsaved: Boolean, onDone: (SettingsInput.Applied) -> Unit) {
    var taper by rememberSaveable { mutableStateOf(current.taper) }
    EditPage("How it drops", { onDone(SettingsInput.Applied(current.copy(taper = taper), taper != current.taper, emptyList())) }) {
        ScreenTitle(
            "How fast should it drop?",
            subtitle = "From $allowance a day, since ${Fmt.dayMonth(current.planStart)}." + if (allowanceUnsaved) " That new start isn't saved yet." else "",
        )
        TaperType.entries.forEach { t ->
            OptionCard(t.label, t.description, t == taper, { taper = t }, trailing = "Weeks 1–4: " + PlanMath.firstWeeks(allowance, t).joinToString(" → "))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackPage(current: UserSettings, onDone: (SettingsInput.Applied) -> Unit) {
    val quick = remember { SettingsInput.quickCurrencies() }
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
                Footnote("Left empty, it's called “${SettingsInput.GOAL_NAME_BLANK}”.", color = c.inkSoft)
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

private fun Modifier.paddingTop() = this.padding(top = ExSpace.xs)
