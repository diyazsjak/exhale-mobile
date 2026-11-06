package app.exhale.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import app.exhale.domain.NameCheck
import app.exhale.domain.TriggerEntry
import app.exhale.domain.Triggers
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.DialogAction
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExChip
import app.exhale.ui.components.ExChoiceDialog
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExTextField
import app.exhale.ui.components.ExToastState
import app.exhale.ui.components.FieldNote
import app.exhale.ui.components.Footnote
import app.exhale.ui.components.HandleRow
import app.exhale.ui.components.Overline
import app.exhale.ui.components.ReorderColumn
import app.exhale.ui.components.ScreenTitle
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType

/** What Settings → Triggers can change. Each applies at once (see [app.exhale.ui.ExhaleViewModel]). */
class TriggerActions(
    val add: (name: String) -> Unit = {},
    val rename: (key: String, name: String) -> Unit = { _, _ -> },
    val setHidden: (key: String, hidden: Boolean) -> Unit = { _, _ -> },
    val move: (from: Int, to: Int) -> Unit = { _, _ -> },
    val delete: (key: String) -> Unit = {},
)

/** How long a hidden (or shown-again) row takes to leave its list. */
private const val MOVE_OUT_MS = 280

private fun usesText(n: Int) = when (n) {
    0 -> "Not used yet"
    1 -> "Tagged once"
    else -> "Tagged $n times"
}

/**
 * Settings → Triggers: the shown triggers in chip order (drag the handle to reorder, the eye hides
 * one), the hidden ones, and "Add a trigger". Changes apply at once. Tapping one opens [TriggerPage].
 */
@Composable
internal fun TriggersPage(
    triggers: List<TriggerEntry>,
    uses: Map<String, Int>,
    actions: TriggerActions,
    onOpen: (key: String?) -> Unit,
    onBack: () -> Unit,
) {
    val c = Ex.colors
    // Hiding (or showing again) is saved at once; the screen then plays it out. The eye turns, the row
    // drifts toward it and fades as its space closes, then it grows into the other list.
    // Key → the hidden state it had: while in here, the row stays in its old list to play its exit.
    var leaving by remember { mutableStateOf(mapOf<String, Boolean>()) }
    // Keys that just moved, which grow into their new list.
    var arriving by remember { mutableStateOf(setOf<String>()) }
    val (hidden, shown) = triggers
        .map { e -> leaving[e.key]?.let { was -> e.copy(hidden = was) } ?: e }
        .partition { it.hidden }

    // One row for both lists: shown ones have a handle and hide; hidden ones show again and say what they keep.
    @Composable
    fun Row(e: TriggerEntry, handle: Modifier? = null, dragging: Boolean = false) {
        val label = e.trigger.label
        val n = uses[e.key] ?: 0
        val going = e.key in leaving
        LaunchedEffect(going) {
            if (going) {
                delay(MOVE_OUT_MS.toLong())
                leaving = leaving - e.key
                arriving = arriving + e.key
            }
        }
        val arrive = remember { MutableTransitionState(e.key !in arriving).apply { targetState = true } }
        LaunchedEffect(arrive.isIdle) { if (arrive.isIdle) arriving = arriving - e.key }
        AnimatedVisibility(
            arrive,
            enter = expandVertically(tween(240)) + fadeIn(tween(200, delayMillis = 80)),
        ) {
            AnimatedVisibility(
                !going,
                exit = slideOutHorizontally(tween(MOVE_OUT_MS)) { it / 12 } + fadeOut(tween(MOVE_OUT_MS - 60)) +
                    shrinkVertically(tween(MOVE_OUT_MS - 40, delayMillis = 40)),
            ) {
                HandleRow(
                    label, { onOpen(e.key) },
                    if (e.hidden != going) ExIcons.EyeOff else ExIcons.Eye,
                    if (e.hidden) "Show $label" else "Hide $label",
                    {
                        actions.setHidden(e.key, !e.hidden)
                        leaving = leaving + (e.key to e.hidden)
                    },
                    handle = handle, dragging = dragging,
                    badge = "Yours".takeIf { e.trigger.builtIn == null },
                    sub = if (e.hidden && n > 0) "${usesText(n)}, still on Rhythm" else null,
                    dimmed = e.hidden,
                )
            }
        }
    }

    EditPage("Triggers", onBack, savesOnBack = false) {
        ScreenTitle(
            "Your triggers",
            subtitle = "The chips you can tag a cigarette or a craving with, in this order. " +
                "After ${Triggers.CHIPS_COLLAPSED}, the rest wait behind “more”.",
        )
        Overline("Shown · ${shown.size}", Modifier.padding(top = ExSpace.xs))
        if (shown.isEmpty()) {
            ExCard { Text("None shown. Tagging is optional, so that's fine.", style = ExType.caption, color = c.inkSoft) }
        } else {
            ExCard(padding = ExSpace.sm) {
                ReorderColumn(shown, keyOf = { it.key }, onMove = actions.move) { e, handle, dragging -> Row(e, handle, dragging) }
            }
        }
        // The Hidden list folds in with its first trigger and away with its last.
        var lastHidden by remember { mutableStateOf(hidden) }
        if (hidden.isNotEmpty()) lastHidden = hidden
        AnimatedVisibility(
            hidden.isNotEmpty(),
            enter = expandVertically(tween(240)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(240)) + fadeOut(tween(160)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ExSpace.md)) {
                Overline("Hidden · ${lastHidden.size}", Modifier.padding(top = ExSpace.xs))
                ExCard(padding = ExSpace.sm) { lastHidden.forEach { key(it.key) { Row(it) } } }
            }
        }
        ExButton(
            "Add a trigger", { onOpen(null) }, Modifier.fillMaxWidth().padding(top = ExSpace.xs),
            style = ButtonStyle.Secondary, icon = ExIcons.Plus,
        )
    }
}

/**
 * One trigger: the user's own can be renamed (saved on the way back, like Settings' other pages);
 * built-ins keep their name. Any can be hidden or shown again, and deleted while it has tagged nothing.
 * With [key] null it adds a new one instead.
 */
@Composable
internal fun TriggerPage(
    triggers: List<TriggerEntry>,
    key: String?,
    uses: Map<String, Int>,
    actions: TriggerActions,
    toast: ExToastState,
    onBack: () -> Unit,
) {
    // Once leaving, the page keeps the list it had: while it slides out, a deleted trigger would turn it
    // into "Add a trigger", and a just-added name would read as taken.
    var leaving by remember { mutableStateOf<List<TriggerEntry>?>(null) }
    val list = leaving ?: triggers
    val back = { leaving = triggers; onBack() }
    val entry = list.firstOrNull { it.key == key }
    if (entry == null) NewTriggerPage(list, actions, toast, back)
    else EditTriggerPage(list, entry, uses, actions, toast, back)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTriggerPage(triggers: List<TriggerEntry>, actions: TriggerActions, toast: ExToastState, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val check = Triggers.check(triggers, name)
    EditPage("Add a trigger", onBack, savesOnBack = false) {
        ScreenTitle("What sets it off?", subtitle = "A word or two, like “Phone calls”. It joins the chips you see after logging.")
        Column(verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            ExTextField("Name", name, { name = it }, placeholder = "Phone calls", maxLength = Triggers.NAME_MAX)
            FieldNote(
                when (check) {
                    is NameCheck.Hidden -> "“${check.entry.trigger.label}” is hidden. Adding it shows it again."
                    is NameCheck.Taken -> "You already have “${check.entry.trigger.label}”."
                    else -> ""
                }
            )
        }
        val ideas = Triggers.ideas(triggers)
        if (ideas.isNotEmpty()) {
            Overline("Ideas", Modifier.padding(top = ExSpace.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ExSpace.xs), verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
                ideas.forEach { idea -> ExChip(idea, (check as? NameCheck.Ok)?.name == idea, { name = idea }) }
            }
        }
        // What Add will say, or null while there's nothing it can add.
        val added = when (check) {
            is NameCheck.Ok -> "Added “${check.name}”"
            is NameCheck.Hidden -> "“${check.entry.trigger.label}” is shown again"
            else -> null
        }
        ExButton(
            "Add",
            { added?.let { toast.show(it); actions.add(name); onBack() } },
            Modifier.fillMaxWidth().padding(top = ExSpace.sm),
            enabled = added != null,
        )
    }
}

@Composable
private fun EditTriggerPage(
    triggers: List<TriggerEntry>,
    entry: TriggerEntry,
    uses: Map<String, Int>,
    actions: TriggerActions,
    toast: ExToastState,
    onBack: () -> Unit,
) {
    val t = entry.trigger
    val own = t.builtIn == null
    var name by rememberSaveable(t.key) { mutableStateOf(t.label) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    val check = Triggers.check(triggers, name, except = t.key)
    val used = usesText(uses[t.key] ?: 0)

    // Going back saves a new name when it can, and says so; otherwise it keeps the old one.
    fun leave(then: String? = null) {
        val note = when {
            !own || (check as? NameCheck.Ok)?.name == t.label -> then
            check is NameCheck.Ok -> { actions.rename(t.key, check.name); listOfNotNull("Saved", then).joinToString(". ") }
            else -> listOfNotNull(then, "The name stays “${t.label}”.").joinToString(". ")
        }
        note?.let(toast::show)
        onBack()
    }

    EditPage(if (own) "Your trigger" else "Built-in trigger", { leave() }, savesOnBack = own) {
        if (own) {
            ScreenTitle("Rename it", subtitle = "$used. Tags keep up with the new name.")
            Column(verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
                ExTextField("Name", name, { name = it }, placeholder = t.label, maxLength = Triggers.NAME_MAX)
                FieldNote(
                    when (check) {
                        NameCheck.Blank -> "Enter a name. Going back keeps “${t.label}”."
                        is NameCheck.Taken -> "You already have “${check.entry.trigger.label}”. Going back keeps “${t.label}”."
                        is NameCheck.Hidden -> "A hidden trigger is called “${check.entry.trigger.label}”. Going back keeps “${t.label}”."
                        is NameCheck.Ok -> ""
                    }
                )
            }
        } else {
            ScreenTitle(t.label, subtitle = "$used. Built-in triggers keep their name.")
        }
        ExButton(
            if (entry.hidden) "Show it again" else "Hide from the chips",
            {
                actions.setHidden(t.key, !entry.hidden)
                leave(if (entry.hidden) "Shown again" else "Hidden")
            },
            Modifier.fillMaxWidth().padding(top = ExSpace.xs),
            style = ButtonStyle.Secondary,
            icon = if (entry.hidden) ExIcons.Eye else ExIcons.EyeOff,
        )
        if (Triggers.canDelete(t.key, uses)) {
            ExButton("Delete trigger", { askDelete = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
        } else {
            Footnote("It has tagged something, so it can be hidden but not deleted. Its history stays on Rhythm.")
        }
    }

    if (askDelete) {
        ExChoiceDialog(
            "Delete “${t.label}”?",
            "It hasn't tagged anything yet, so nothing else changes.",
            listOf(
                DialogAction("Delete it", ButtonStyle.Primary) {
                    askDelete = false
                    actions.delete(t.key)
                    toast.show("Deleted “${t.label}”")
                    onBack()
                },
                DialogAction("Keep it", ButtonStyle.Ghost) { askDelete = false },
            ),
            onDismiss = { askDelete = false },
        )
    }
}
