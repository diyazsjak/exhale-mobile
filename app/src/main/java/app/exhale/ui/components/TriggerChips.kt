package app.exhale.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.exhale.domain.Trigger
import app.exhale.domain.Triggers
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType

/**
 * Wrapping set of trigger chips in the user's order ([Triggers.chipRow]): the first nine, then a
 * "N more" chip that opens the rest in place. With [onAdd], a "Your own" chip at the end names a new
 * trigger and passes the tidied name: inline, it gives way to a field that unfolds under the chips
 * (Back cancels); with [addInDialog] (a screen whose bottom the keyboard would cover) it opens a
 * dialog that sits above the keyboard. [onEditing] says when the inline field opens and closes, so a
 * card that closes on its own can wait. Chips that arrive after the first frame (a trigger just
 * added) pop in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggerChips(
    triggers: List<Trigger>,
    selected: Trigger?,
    onPick: (Trigger) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: ((String) -> Unit)? = null,
    addInDialog: Boolean = false,
    onEditing: (Boolean) -> Unit = {},
    /** Screenshots: open the field with this text. */
    initialDraft: String? = null,
) {
    val c = Ex.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    // null while not naming a new trigger.
    var draft by rememberSaveable { mutableStateOf(initialDraft) }
    // The text the field shows, kept while it folds away after Add or Cancel.
    var shownDraft by remember { mutableStateOf(initialDraft ?: "") }
    draft?.let { shownDraft = it }
    val editing by rememberUpdatedState(onEditing)
    LaunchedEffect(draft != null) { if (!addInDialog) editing(draft != null) }
    BackHandler(enabled = draft != null && !addInDialog) { draft = null }
    val firstKeys = remember { triggers.map { it.key }.toSet() }

    val row = Triggers.chipRow(triggers, selected, expanded)
    Column(modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ExSpace.xs),
            verticalArrangement = Arrangement.spacedBy(ExSpace.xs),
        ) {
            row.visible.forEach { t ->
                key(t.key) {
                    if (t.key in firstKeys) {
                        ExChip(t.label, selected == t, { onPick(t) }, accent = c.flare)
                    } else {
                        // A trigger just added: it grows into its place.
                        AnimatedVisibility(
                            remember { MutableTransitionState(false).apply { targetState = true } },
                            enter = fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.6f),
                        ) { ExChip(t.label, selected == t, { onPick(t) }, accent = c.flare) }
                    }
                }
            }
            if (row.more > 0) {
                QuietChip("${row.more} more", null, "Show ${row.more} more triggers") { expanded = true }
            }
            if (onAdd != null) {
                AnimatedVisibility(
                    draft == null || addInDialog,
                    enter = fadeIn(tween(200, delayMillis = 120)) + scaleIn(tween(200, delayMillis = 120), initialScale = 0.85f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f),
                ) {
                    QuietChip("Your own", ExIcons.Plus, "Add your own trigger") { draft = "" }
                }
            }
        }
        if (onAdd != null && !addInDialog) {
            // The field unfolds under the chips as "Your own" fades, and folds back after Add or Cancel.
            AnimatedVisibility(
                draft != null,
                enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(200, delayMillis = 60)),
                exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
            ) {
                Column(Modifier.padding(top = ExSpace.xs), verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
                    ChipField(
                        shownDraft, { if (draft != null) draft = it.take(Triggers.NAME_MAX) },
                        onDone = {
                            draft?.let { d -> Triggers.clean(d)?.let(onAdd) }
                            draft = null
                        },
                    )
                    Text(
                        "${shownDraft.length} / ${Triggers.NAME_MAX} · It joins your list for next time.",
                        style = ExType.caption, color = c.inkFaint, modifier = Modifier.padding(horizontal = ExSpace.xxs),
                    )
                }
            }
        }
    }
    if (onAdd != null && addInDialog && draft != null) {
        NameTriggerDialog(
            onAdd = { name -> draft = null; onAdd(name) },
            onDismiss = { draft = null },
        )
    }
}

/** "Your own" as a dialog: a name field with the keyboard up, Add and Cancel. */
@Composable
private fun NameTriggerDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val clean = Triggers.clean(name)
    val add = { clean?.let(onAdd); Unit }
    ExInputDialog(
        "Your own trigger",
        onDismiss,
        listOf(
            DialogAction("Add", ButtonStyle.Primary, enabled = clean != null, onClick = add),
            DialogAction("Cancel", ButtonStyle.Ghost, onClick = onDismiss),
        ),
    ) {
        ExTextField(
            "Name", name, { name = it }, placeholder = "Phone calls", maxLength = Triggers.NAME_MAX,
            autoFocus = true, onDone = add, sentences = true,
        )
        Text("It joins your list for next time.", style = ExType.caption, color = Ex.colors.inkFaint)
    }
}

/** An action pill among chips ("Your own", "3 more"): no fill, a faint outline, `ink-soft` label. */
@Composable
private fun QuietChip(text: String, icon: ImageVector?, description: String, onClick: () -> Unit) {
    val c = Ex.colors
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .scale(pressScale(source))
            .heightIn(min = ExSize.chip)
            .clip(ExRadius.pill)
            .border(ExSize.hairline, c.inkFaint, ExRadius.pill)
            .clickable(source, ripple(), role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(start = if (icon != null) ExSpace.sm else ExSpace.md, end = ExSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(ExSize.iconSmall), tint = c.inkSoft)
        Text(text, style = ExType.labelSmall, color = c.inkSoft)
    }
}

/**
 * The "Your own" chip while naming: a full-width pill field with a `sky` ring and a small button that
 * says Add, or Cancel while the field is empty.
 */
@Composable
private fun ChipField(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val c = Ex.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ready = Triggers.clean(value) != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ExSize.chip)
            .clip(ExRadius.pill)
            .background(c.surface)
            .border(ExSize.ring, c.sky, ExRadius.pill)
            .padding(start = ExSpace.md, end = ExSpace.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Name it, like “Phone calls”", style = ExType.labelSmall, color = c.inkFaint)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = ExType.labelSmall.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "New trigger name" },
            )
        }
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier
                .padding(start = ExSpace.xs)
                .scale(pressScale(source))
                .height(32.dp)
                .clip(ExRadius.pill)
                .background(if (ready) c.primary else c.surfaceHigh)
                .clickable(source, ripple(), role = Role.Button, onClick = onDone)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (ready) "Add" else "Cancel", style = ExType.labelSmall, color = if (ready) c.onPrimary else c.ink)
        }
    }
}
