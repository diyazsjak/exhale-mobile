package app.exhale.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType

enum class ButtonStyle {
    /** Main action of a screen. Inverted ink: white on dark, black on light. One per screen. */
    Primary,
    /** Other actions. Surface fill. */
    Secondary,
    /** Secondary inside a card (which is already `surface`): `surfaceHigh` fill. */
    Tonal,
    /** Oxygen fill for a meaningful action (e.g. "I'm okay now"): `oxy` in dark, `oxyInk` in light. */
    Accent,
    /** Low emphasis text button: skip, cancel. */
    Ghost,
}

enum class ButtonSize(val height: Dp) { Large(ExSize.buttonLarge), Medium(ExSize.buttonMedium) }

/** Squishes slightly while pressed. Shared by all tappable kit components. */
@Composable
internal fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "press")
    return s
}

@Composable
fun ExButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Ex.colors
    val (bg, fg) = when {
        !enabled -> c.surfaceHigh to c.inkFaint
        style == ButtonStyle.Primary -> c.primary to c.onPrimary
        style == ButtonStyle.Secondary -> c.surface to c.ink
        style == ButtonStyle.Tonal -> c.surfaceHigh to c.ink
        style == ButtonStyle.Accent -> c.oxyInk to c.onOxy
        else -> Color.Transparent to c.inkSoft
    }
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .scale(pressScale(source))
            .height(size.height)
            .clip(ExRadius.pill)
            .background(bg)
            .clickable(source, ripple(color = fg), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ExSpace.xl),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = fg)
        Text(text, style = if (size == ButtonSize.Large) ExType.label else ExType.labelSmall, color = fg, textAlign = TextAlign.Center)
    }
}

/** Round icon button, like the back arrow in the header. */
@Composable
fun ExIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    tint: Color = Ex.colors.ink,
) {
    val c = Ex.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .scale(pressScale(source))
            .size(ExSize.iconButton)
            .clip(ExRadius.pill)
            .background(if (filled) c.surface else Color.Transparent)
            .clickable(source, ripple(), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(ExSize.icon), tint = tint)
    }
}

/**
 * Inline text link for a way onward from a row of text, e.g. "All history ›". `labelSmall` in
 * `ink` with a chevron; keeps a 40dp touch height without adding visual weight.
 */
@Composable
fun ExLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ex.colors
    Row(
        modifier
            .heightIn(min = ExSize.chip)
            .clip(ExRadius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text, style = ExType.labelSmall, color = c.ink)
        Icon(ExIcons.Forward, null, Modifier.size(ExSize.iconSmall), tint = c.ink)
    }
}
