package app.exhale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType

/**
 * Every screen sits in this: the shared background, status-bar inset, side gutter, and room
 * for the floating tab bar (pass a smaller [bottomClearance] on screens without it). [header] is a row for back/close/settings buttons and a label.
 * [background] draws behind the content (e.g. the Air particle field) on top of the shared bg.
 */
@Composable
fun ExScreen(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    bottomClearance: Dp = ExSize.tabBarClearance,
    header: (@Composable RowScope.() -> Unit)? = null,
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ex.colors
    Box(modifier.fillMaxSize().background(c.bg)) {
        background?.invoke(this)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = ExSpace.gutter)
                .navigationBarsPadding()
                .padding(top = ExSpace.xs, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(ExSpace.md),
        ) {
            if (header != null) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = ExSize.iconButton),
                    verticalAlignment = Alignment.CenterVertically,
                    content = header,
                )
            }
            content()
        }
    }
}

/** Grouped content on a surface. Optional [onClick] makes the whole card tappable. */
@Composable
fun ExCard(
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = ExSpace.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ex.colors
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.scale(pressScale(source)) else Modifier)
            .clip(ExRadius.lg)
            .background(color ?: c.surface)
            .then(if (onClick != null) Modifier.clickable(source, ripple(), onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(ExSpace.xs),
        content = content,
    )
}

/** Small uppercase label above a group. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(text.uppercase(), style = ExType.overline, color = color ?: Ex.colors.inkSoft, modifier = modifier)
}

/** Serif screen title with optional supporting line. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
        Text(title, style = ExType.title, color = Ex.colors.ink)
        if (subtitle != null) Text(subtitle, style = ExType.body, color = Ex.colors.inkSoft)
    }
}

/** Big value + caption pair used in stat rows. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color? = null) {
    Column(modifier) {
        Text(value, style = ExType.number, color = color ?: Ex.colors.ink)
        Text(label, style = ExType.caption, color = Ex.colors.inkSoft)
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

/** Pushes following header items to the end. */
@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** Header label centered between header buttons. */
@Composable
fun RowScope.HeaderLabel(text: String) {
    Text(text, style = ExType.labelSmall, color = Ex.colors.inkSoft, modifier = Modifier.weight(1f).padding(horizontal = ExSpace.xs), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
