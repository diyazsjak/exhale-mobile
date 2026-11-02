package app.exhale.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius

import app.exhale.ui.theme.ExType

/** Filled text field: label above, soft surface box, accent ring while focused. */
@Composable
fun ExTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    type: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    val c = Ex.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = ExType.caption, color = c.inkSoft)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = ExType.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            interactionSource = source,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(ExRadius.md)
                        .background(c.surface)
                        .border(1.5.dp, if (focused) c.sky else Color.Transparent, ExRadius.md)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = ExType.body, color = c.inkFaint)
                    inner()
                }
            },
        )
    }
}

/** Big serif number with round − / + buttons. For counts adjusted a few steps at a time. */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val c = Ex.colors
    Row(
        modifier.fillMaxWidth().semantics { stateDescription = "$value $unit" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(ExIcons.Close, "Decrease", enabled = value > range.first, minus = true) { onChange(value - 1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = ExType.display.copy(fontSize = 96.sp), color = c.ink)
            Text(unit, style = ExType.caption, color = c.inkSoft)
        }
        StepButton(ExIcons.Plus, "Increase", enabled = value < range.last, minus = false) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, minus: Boolean, onClick: () -> Unit) {
    val c = Ex.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .scale(pressScale(source))
            .size(64.dp)
            .clip(ExRadius.pill)
            .background(c.surface)
            .clickable(source, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) c.ink else c.inkFaint
        if (minus) Box(Modifier.width(18.dp).height(1.8.dp).clip(ExRadius.pill).background(tint))
        else Icon(icon, null, Modifier.size(24.dp), tint = tint)
    }
}

/** iOS-style segmented control with a sliding thumb. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ex.colors
    val track = if (c.isDark) c.surface else c.surfaceHigh
    val thumb = if (c.isDark) c.surfaceHigh else c.surface
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(ExRadius.pill)
            .background(track)
            .padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "thumb")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(ExRadius.pill).background(thumb))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ExRadius.pill)
                        .clickable(role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = ExType.labelSmall, color = if (i == selected) c.ink else c.inkSoft)
                }
            }
        }
    }
}

/** Segmented progress for multi-step flows. Filled segments cool from ember to oxygen. */
@Composable
fun StepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Ex.colors
    Row(
        modifier.semantics { contentDescription = "Step $step of $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(ExRadius.pill)
                    .background(if (i < step) c.heat(1f - i / (total - 1f).coerceAtLeast(1f)) else c.surfaceHigh)
            )
        }
    }
}

