package app.exhale.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExColors
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import kotlinx.coroutines.delay

/**
 * Filled text field: label above, soft surface box, accent ring while focused. With [maxLength],
 * input is cut at that many characters and a "12 / 40" counter sits beside the label. [suffix] is a
 * unit or currency shown at the end of the box ("USD", "cigs").
 */
@Composable
fun ExTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    type: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    maxLength: Int? = null,
    suffix: String? = null,
    /** Put the cursor here (and the keyboard up) when it first shows. */
    autoFocus: Boolean = false,
    /** The keyboard's Done key, when set. */
    onDone: (() -> Unit)? = null,
    /** Start sentences with a capital, for names. */
    sentences: Boolean = false,
) {
    val c = Ex.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = ExType.caption, color = c.inkSoft, modifier = Modifier.weight(1f))
            if (maxLength != null) Text("${value.length} / $maxLength", style = ExType.caption, color = c.inkFaint)
        }
        BasicTextField(
            value = value,
            onValueChange = { onChange(if (maxLength != null) it.take(maxLength) else it) },
            singleLine = true,
            textStyle = ExType.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(
                keyboardType = type,
                capitalization = if (sentences) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            interactionSource = source,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(ExRadius.md)
                        .background(c.surface)
                        .border(ExSize.ring, if (focused) c.sky else Color.Transparent, ExRadius.md)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) Text(placeholder, style = ExType.body, color = c.inkFaint)
                            inner()
                        }
                        if (suffix != null) Text(suffix, style = ExType.labelSmall, color = c.inkSoft, modifier = Modifier.padding(start = ExSpace.xs))
                    }
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
        StepButton(icon = null, "Decrease", enabled = value > range.first) { onChange(value - 1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = ExType.display.copy(fontSize = 96.sp), color = c.ink)
            Text(unit, style = ExType.caption, color = c.inkSoft)
        }
        StepButton(ExIcons.Plus, "Increase", enabled = value < range.last) { onChange(value + 1) }
    }
}

/** A 64dp round [Stepper] button: [icon], or a drawn minus bar when null (thinner than [ExIcons.Minus]). */
@Composable
private fun StepButton(icon: ImageVector?, description: String, enabled: Boolean, onClick: () -> Unit) {
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
        if (icon == null) Box(Modifier.width(18.dp).height(1.8.dp).clip(ExRadius.pill).background(tint))
        else Icon(icon, null, Modifier.size(ExSize.icon), tint = tint)
    }
}

/** Segmented track tones: the track sits a step below cards, the thumb a step above it. */
private val ExColors.segmentTrack: Color get() = if (isDark) surface else surfaceHigh
private val ExColors.segmentThumb: Color get() = if (isDark) surfaceHigh else surface

/** The pill track shared by [SegmentedControl] and [ActiveFilter]. */
private fun Modifier.segmentTrack(c: ExColors): Modifier =
    fillMaxWidth().height(ExSize.segment).clip(ExRadius.pill).background(c.segmentTrack)

/** iOS-style segmented control with a sliding thumb. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ex.colors
    BoxWithConstraints(modifier.segmentTrack(c).padding(ExSpace.xxs)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "thumb")
        // selected outside the options (e.g. -1) means nothing is selected: no thumb.
        if (selected in options.indices) Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(ExRadius.pill).background(c.segmentThumb))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ExRadius.pill)
                        .selectable(i == selected, role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = ExType.labelSmall, color = if (i == selected) c.ink else c.inkSoft)
                }
            }
        }
    }
}

/** Segmented progress for multi-step flows. Done segments are `ink`, the rest `surfaceHigh`. */
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
                    .background(if (i < step) c.ink else c.surfaceHigh)
            )
        }
    }
}

/**
 * Stands in for a [SegmentedControl] while something more specific is filtering the screen
 * (e.g. a day picked in the week strip): same track and height, the filter's [text], and a thumb
 * button that clears it.
 */
@Composable
fun ActiveFilter(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ex.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .segmentTrack(c)
            .padding(start = ExSpace.md, end = ExSpace.xxs, top = ExSpace.xxs, bottom = ExSpace.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = ExType.labelSmall, color = c.ink, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .scale(pressScale(source))
                .fillMaxHeight()
                .clip(ExRadius.pill)
                .background(c.segmentThumb)
                .clickable(source, ripple(), role = Role.Button, onClick = onAction)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, style = ExType.labelSmall, color = c.ink)
        }
    }
}

/**
 * A count inside a list card: label and an optional caption on the left, a light − value + pill
 * on the right. Lighter than [Stepper], for settings changed a step or two at a time. Holding a
 * button repeats. [changed] shows the value in `sky` ink, e.g. while it waits to be saved.
 */
@Composable
fun CounterRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    caption: String? = null,
    changed: Boolean = false,
) {
    val c = Ex.colors
    val line = c.line
    Row(
        modifier.fillMaxWidth().heightIn(min = ExSize.row).padding(start = ExSpace.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ExSpace.xs),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = ExType.body, color = c.ink)
            if (caption != null) Text(caption, style = ExType.caption, color = c.inkSoft)
        }
        Row(
            // A 36dp hairline pill drawn inside 44dp buttons, so the touch area stays 44dp.
            Modifier.height(ExSize.touch).drawBehind {
                val inset = 4.dp.toPx()
                val w = ExSize.hairline.toPx()
                val h = size.height - 2 * inset
                drawRoundRect(
                    line,
                    topLeft = Offset(inset + w / 2, inset + w / 2),
                    size = Size(size.width - 2 * inset - w, h - w),
                    cornerRadius = CornerRadius(h / 2),
                    style = Stroke(w),
                )
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CounterButton(ExIcons.Minus, "Decrease $label", -1, value, range, onChange)
            Text(
                "$value",
                style = ExType.numberSmall.copy(fontSize = ExType.body.fontSize),
                color = if (changed) c.skyInk else c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 30.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            CounterButton(ExIcons.Plus, "Increase $label", 1, value, range, onChange)
        }
    }
}

@Composable
private fun CounterButton(icon: ImageVector, description: String, step: Int, value: Int, range: IntRange, onChange: (Int) -> Unit) =
    RepeatButton(icon, description, enabled = value + step in range, onStep = { onChange(value + step) })

/**
 * A small round step button (44dp touch, 16dp icon) that repeats while held; see [RepeatTarget].
 * Used by [CounterRow].
 */
@Composable
internal fun RepeatButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ex.colors
    RepeatTarget(
        enabled, description, onStep, modifier.size(ExSize.touch),
        indication = ripple(bounded = false, radius = 18.dp),
    ) {
        Icon(icon, null, Modifier.size(ExSize.iconSmall), tint = if (enabled) c.inkSoft else c.inkFaint.copy(alpha = 0.5f))
    }
}

/**
 * A tap target that steps once on tap, then (with [repeat]) every 70ms after 420ms of holding,
 * while [enabled]. Behind [RepeatButton] and the halves of [TimeWheel] (tap only).
 */
@Composable
internal fun RepeatTarget(
    enabled: Boolean,
    description: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    indication: Indication? = null,
    repeat: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val canStep by rememberUpdatedState(enabled)
    val step by rememberUpdatedState(onStep)
    // Set once holding has stepped, so the click that ends the press doesn't step again.
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed || !repeat) return@LaunchedEffect
        repeated = false
        delay(420)
        while (canStep) {
            repeated = true
            step()
            delay(70)
        }
    }
    Box(
        modifier
            .clickable(source, indication, enabled = enabled, role = Role.Button) {
                if (repeated) repeated = false else onStep()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A note under a field or group about a value that can't be saved as typed, and what is kept
 * instead. `ink` caption after a small `sky` dot: noticeable, never red. Shows nothing when empty.
 */
@Composable
fun FieldNote(text: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    val c = Ex.colors
    Row(modifier.padding(horizontal = ExSpace.xxs), horizontalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(ExRadius.pill).background(c.sky))
        Text(text, style = ExType.caption, color = c.ink)
    }
}

/**
 * An on/off switch: a [ExSize.switchWidth] × [ExSize.switchHeight] pill with a round thumb that
 * slides across on a spring. Off: `surfaceHigh` track, smaller `inkSoft` thumb on the left. On:
 * `primary` track (inverted ink, like the Primary button), `onPrimary` thumb on the right. Neutral on
 * purpose: on or off isn't smoke or recovery, so no accent. With [onChange] null it only draws, for a
 * row that takes the tap itself ([SwitchRow]).
 */
@Composable
fun ExSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Ex.colors
    // The thumb grows as it slides across; it stays centred in the track's height either way.
    val on = 24.dp
    val off = 18.dp
    val inset = (ExSize.switchHeight - on) / 2
    val motion = spring<Dp>(dampingRatio = 0.8f, stiffness = 500f)
    val thumb by animateDpAsState(if (checked) on else off, motion, label = "switch-thumb-size")
    val x by animateDpAsState(if (checked) ExSize.switchWidth - inset - on else inset + (on - off) / 2, motion, label = "switch-thumb")
    val track by animateColorAsState(if (checked) c.primary else c.surfaceHigh, tween(160), label = "switch-track")
    val dot by animateColorAsState(if (checked) c.onPrimary else c.inkSoft, tween(160), label = "switch-dot")
    val toggle = if (onChange == null) Modifier else Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)
    // A 44dp touch area around the 32dp track when it takes taps itself.
    Box(modifier.then(toggle).heightIn(min = if (onChange == null) 0.dp else ExSize.touch), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(ExSize.switchWidth)
                .height(ExSize.switchHeight)
                .clip(ExRadius.pill)
                .background(track),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.offset(x = x).size(thumb).clip(ExRadius.pill).background(dot))
        }
    }
}

/**
 * A row in a list card that turns something on or off: `body` label, an optional `caption` line
 * under it (it wraps, unlike [ListRow]'s), and an [ExSwitch] on the right. The whole row takes the
 * tap and reads to screen readers as one switch.
 */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, sub: String? = null) {
    val c = Ex.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(ExRadius.sm)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = ExSize.row)
            .padding(horizontal = 4.dp, vertical = if (sub != null) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ExSpace.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = ExType.body, color = c.ink)
            if (sub != null) Text(sub, style = ExType.caption, color = c.inkSoft)
        }
        ExSwitch(checked, onChange = null, Modifier.clearAndSetSemantics {})
    }
}
