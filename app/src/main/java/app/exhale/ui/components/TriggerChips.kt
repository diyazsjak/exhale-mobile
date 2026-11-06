package app.exhale.ui.components

import androidx.activity.compose.BackHandler
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
 * "N more" chip that opens the rest in place. With [onAdd], a "Your own" chip at the end turns into a
 * field to name a new trigger; Add (or the keyboard's Done) passes the tidied name, Back cancels.
 * [onEditing] says when that field opens and closes, so a card that closes on its own can wait.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggerChips(
    triggers: List<Trigger>,
    selected: Trigger?,
    onPick: (Trigger) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: ((String) -> Unit)? = null,
    onEditing: (Boolean) -> Unit = {},
    /** Screenshots: open the field with this text. */
    initialDraft: String? = null,
) {
    val c = Ex.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    // null while not naming a new trigger.
    var draft by rememberSaveable { mutableStateOf(initialDraft) }
    val editing by rememberUpdatedState(onEditing)
    LaunchedEffect(draft != null) { editing(draft != null) }
    BackHandler(enabled = draft != null) { draft = null }

    val row = Triggers.chipRow(triggers, selected, expanded)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ExSpace.xs),
            verticalArrangement = Arrangement.spacedBy(ExSpace.xs),
        ) {
            row.visible.forEach { t -> ExChip(t.label, selected == t, { onPick(t) }, accent = c.flare) }
            if (row.more > 0) {
                QuietChip("${row.more} more", null, "Show ${row.more} more triggers") { expanded = true }
            }
            if (onAdd != null) {
                val d = draft
                if (d == null) {
                    QuietChip("Your own", ExIcons.Plus, "Add your own trigger") { draft = "" }
                } else {
                    ChipField(
                        d, { draft = it.take(Triggers.NAME_MAX) },
                        onDone = {
                            Triggers.clean(d)?.let(onAdd)
                            draft = null
                        },
                    )
                }
            }
        }
        draft?.let { d ->
            Text(
                "${d.length} / ${Triggers.NAME_MAX} · It joins your list for next time.",
                style = ExType.caption, color = c.inkFaint, modifier = Modifier.padding(horizontal = ExSpace.xxs),
            )
        }
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
            .padding(start = if (icon != null) 12.dp else 16.dp, end = 16.dp),
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
            .padding(start = 16.dp, end = 4.dp),
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
