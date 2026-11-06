package app.exhale.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.exhale.domain.Trigger
import app.exhale.domain.TriggerEntry
import app.exhale.domain.Triggers
import app.exhale.ui.components.BackHeader
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.DialogAction
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExChip
import app.exhale.ui.components.ExChoiceDialog
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
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
    val add: (String) -> Trigger? = { null },
    val rename: (key: String, name: String) -> Unit = { _, _ -> },
    val setHidden: (key: String, hidden: Boolean) -> Unit = { _, _ -> },
    val move: (from: Int, to: Int) -> Unit = { _, _ -> },
    val delete: (key: String) -> Unit = {},
)

/** Names offered when adding one, left out once the list has them. */
private val ideas = listOf("Phone calls", "Waiting", "Work break", "Waking up", "Gaming", "Alone", "Arguments")

private fun usesText(n: Int) = if (n == 1) "Tagged once" else "Tagged $n times"

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
    val shown = triggers.filterNot { it.hidden }
    val hidden = triggers.filter { it.hidden }
    BackHandler(onBack = onBack)
    ExScreen(bottomClearance = ExSpace.xl, header = { BackHeader("Triggers", onBack) }) {
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
                ReorderColumn(shown, key = { it.key }, onMove = actions.move) { e, handle, dragging ->
                    HandleRow(
                        e.trigger.label, { onOpen(e.key) }, ExIcons.Eye, "Hide ${e.trigger.label}", { actions.setHidden(e.key, true) },
                        handle = handle, dragging = dragging, badge = "Yours".takeIf { e.trigger.builtIn == null },
                    )
                }
            }
            Footnote("Drag the handle to change the order. Tap a trigger to rename or delete it.")
        }
        if (hidden.isNotEmpty()) {
            Overline("Hidden · ${hidden.size}", Modifier.padding(top = ExSpace.xs))
            ExCard(padding = ExSpace.sm) {
                hidden.forEach { e ->
                    val n = uses[e.key] ?: 0
                    HandleRow(
                        e.trigger.label, { onOpen(e.key) }, ExIcons.EyeOff, "Show ${e.trigger.label}", { actions.setHidden(e.key, false) },
                        badge = "Yours".takeIf { e.trigger.builtIn == null },
                        sub = if (n > 0) "${usesText(n)}, still on Rhythm" else null,
                        dimmed = true,
                    )
                }
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
    val entry = triggers.firstOrNull { it.key == key }
    if (entry == null) NewTriggerPage(triggers, actions, toast, onBack)
    else EditTriggerPage(triggers, entry, uses, actions, toast, onBack)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTriggerPage(triggers: List<TriggerEntry>, actions: TriggerActions, toast: ExToastState, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val clean = Triggers.clean(name)
    val same = clean?.let { Triggers.named(triggers, it) }
    BackHandler(onBack = onBack)
    ExScreen(bottomClearance = ExSpace.xl, header = { BackHeader("Add a trigger", onBack) }) {
        ScreenTitle("What sets it off?", subtitle = "A word or two, like “Phone calls”. It joins the chips you see after logging.")
        Column(verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            ExTextField("Name", name, { name = it }, placeholder = "Phone calls", maxLength = Triggers.NAME_MAX)
            FieldNote(
                when {
                    same == null -> ""
                    same.hidden -> "“${same.trigger.label}” is hidden. Adding it shows it again."
                    else -> "You already have “${same.trigger.label}”."
                }
            )
        }
        val open = ideas.filter { Triggers.named(triggers, it) == null }
        if (open.isNotEmpty()) {
            Overline("Ideas", Modifier.padding(top = ExSpace.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ExSpace.xs), verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
                open.forEach { idea -> ExChip(idea, Triggers.clean(name) == idea, { name = idea }) }
            }
        }
        ExButton(
            "Add",
            {
                val t = actions.add(name)
                if (t != null) toast.show(if (same?.hidden == true) "“${t.label}” is shown again" else "Added “${t.label}”")
                onBack()
            },
            Modifier.fillMaxWidth().padding(top = ExSpace.sm),
            enabled = clean != null && (same == null || same.hidden),
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
    val c = Ex.colors
    val t = entry.trigger
    val own = t.builtIn == null
    var name by rememberSaveable(t.key) { mutableStateOf(t.label) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    val clean = Triggers.clean(name)
    val taken = clean?.let { Triggers.named(triggers, it, except = t.key) }
    val n = uses[t.key] ?: 0

    // Going back saves a new name when it can, and says so; otherwise it keeps the old one.
    fun leave(then: String? = null) {
        val renamed = own && clean != null && clean != t.label
        when {
            !own || clean == t.label -> if (then != null) toast.show(then)
            renamed && taken == null -> { actions.rename(t.key, clean!!); toast.show(listOfNotNull("Saved", then).joinToString(". ")) }
            else -> toast.show(listOfNotNull(then, "The name stays “${t.label}”.").joinToString(". "))
        }
        onBack()
    }

    BackHandler { leave() }
    ExScreen(
        bottomClearance = ExSpace.xl,
        header = { BackHeader(if (own) "Your trigger" else "Built-in trigger", { leave() }, backLabel = if (own) "Back, saving changes" else "Back") },
    ) {
        if (own) {
            ScreenTitle("Rename it", subtitle = "${if (n > 0) usesText(n) else "Not used yet"}. Tags keep up with the new name.")
            Column(verticalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
                ExTextField("Name", name, { name = it }, placeholder = t.label, maxLength = Triggers.NAME_MAX)
                FieldNote(
                    when {
                        clean == null -> "Enter a name. Going back keeps “${t.label}”."
                        taken != null -> "You already have “${taken.trigger.label}”. Going back keeps “${t.label}”."
                        else -> ""
                    }
                )
            }
        } else {
            ScreenTitle(t.label, subtitle = "${if (n > 0) usesText(n) else "Not used yet"}. Built-in triggers keep their name.")
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
        if (own) Footnote("Changes save when you go back.")
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
