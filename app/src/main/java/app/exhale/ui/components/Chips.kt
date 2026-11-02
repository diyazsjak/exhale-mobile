package app.exhale.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.exhale.domain.Trigger
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType

/** Selectable pill. Selected = soft accent tint with accent text. */
@Composable
fun ExChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val c = Ex.colors
    val a = accent ?: c.sky
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .scale(pressScale(source))
            .heightIn(min = ExSize.chip)
            .clip(ExRadius.pill)
            .background(if (selected) c.tint(a) else c.surface)
            .selectable(selected, source, ripple(), role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ExType.labelSmall, color = if (selected) c.inkOf(a) else c.ink)
    }
}

/** Static label pill: counts, dates, streaks. */
@Composable
fun ExBadge(text: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val c = Ex.colors
    Box(
        modifier
            .clip(ExRadius.pill)
            .background(if (accent != null) c.tint(accent) else c.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = ExType.numberSmall.copy(fontSize = ExType.caption.fontSize), color = accent?.let { c.inkOf(it) } ?: c.inkSoft)
    }
}

/** Wrapping set of trigger chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggerChips(selected: Trigger?, onPick: (Trigger) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ExSpace.xs),
        verticalArrangement = Arrangement.spacedBy(ExSpace.xs),
    ) {
        Trigger.entries.forEach { t -> ExChip(t.label, selected == t, { onPick(t) }, accent = Ex.colors.flare) }
    }
}

/**
 * Selectable card for a choice with a description, e.g. taper plans.
 * [trailing] shows a small preview such as "13 → 12 → 11".
 */
@Composable
fun OptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    accent: Color? = null,
) {
    val c = Ex.colors
    val a = accent ?: c.oxy
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .scale(pressScale(source))
            .clip(ExRadius.md)
            .background(if (selected) c.tint(a) else c.surface)
            .border(1.5.dp, if (selected) a else Color.Transparent, ExRadius.md)
            .selectable(selected, source, ripple(), role = Role.RadioButton, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ExSpace.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ExType.bodyStrong, color = c.ink)
            Text(description, style = ExType.caption, color = c.inkSoft)
            if (trailing != null) Text(trailing, style = ExType.numberSmall, color = if (selected) c.inkOf(a) else c.inkSoft)
        }
        Box(
            Modifier.size(24.dp).clip(ExRadius.pill).background(if (selected) c.inkOf(a) else c.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(ExIcons.Check, null, Modifier.size(16.dp), tint = if (c.isDark) c.bg else Color.White)
        }
    }
}

/** A plain tappable row inside a card: label, optional value, chevron. */
@Composable
fun ListRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, value: String? = null) {
    val c = Ex.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(ExRadius.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ExType.body, color = c.ink, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = ExType.body, color = c.inkSoft, modifier = Modifier.padding(end = 8.dp))
        Icon(ExIcons.Forward, null, Modifier.size(20.dp), tint = c.inkFaint)
    }
}
