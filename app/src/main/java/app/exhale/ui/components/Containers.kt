package app.exhale.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType

/**
 * Every screen sits in this: the shared background, status-bar inset, side gutter, and room
 * for the floating tab bar (pass a smaller [bottomClearance] on screens without it).
 * [header] is a row for back/close/settings buttons and a label. It stays pinned while the content
 * scrolls under it, and a hairline `line` border shows along its bottom while content is under it.
 * [background] draws behind the content (e.g. the Air particle field) on top of the shared bg.
 * Pass [scrollState] to react to scrolling (Air's sky collapses as the screen scrolls).
 * Tabs sit under the shared [ExAppBar] instead of having a [header]: [underAppBar] leaves the same
 * room the header row would take, so content starts in the same place.
 */
@Composable
fun ExScreen(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    bottomClearance: Dp = ExSize.tabBarClearance,
    header: (@Composable RowScope.() -> Unit)? = null,
    background: (@Composable BoxScope.() -> Unit)? = null,
    scrollState: ScrollState? = null,
    underAppBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ex.colors
    val ownScroll = rememberScrollState()
    val st = scrollState ?: ownScroll
    Box(modifier.fillMaxSize().background(c.bg)) {
        background?.invoke(this)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (header != null) {
                val under by remember(scroll) { derivedStateOf { scroll && st.value > 0 } }
                Row(
                    Modifier.scrolledBorder(under).barRow(),
                    verticalAlignment = Alignment.CenterVertically,
                    content = header,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (scroll) Modifier.verticalScroll(st) else Modifier)
                    .padding(horizontal = ExSpace.gutter)
                    .navigationBarsPadding()
                    .padding(top = ExSpace.xs, bottom = bottomClearance),
                verticalArrangement = Arrangement.spacedBy(ExSpace.md),
            ) {
                if (underAppBar) Spacer(Modifier.height(ExSize.iconButton))
                content()
            }
        }
    }
}

/**
 * Grouped content on a surface. Optional [onClick] makes the whole card tappable; [outlined]
 * draws a 1.5dp `ink` ring, e.g. while a tappable card is switched on.
 */
@Composable
fun ExCard(
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = ExSpace.lg,
    outlined: Boolean = false,
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
            .then(if (outlined) Modifier.border(ExSize.ring, c.ink, ExRadius.lg) else Modifier)
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
    val c = Ex.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
        Text(title, style = ExType.title, color = c.ink)
        if (subtitle != null) Text(subtitle, style = ExType.body, color = c.inkSoft)
    }
}

/** Big value + caption pair used in stat rows. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = Ex.colors
    Column(modifier) {
        Text(value, style = ExType.number, color = color ?: c.ink)
        Text(label, style = ExType.caption, color = c.inkSoft)
    }
}

/** Pushes following header items to the end. */
@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** Header label centered between header buttons. */
@Composable
fun RowScope.HeaderLabel(text: String) {
    Text(text, style = ExType.labelSmall, color = Ex.colors.inkSoft, modifier = Modifier.weight(1f).padding(horizontal = ExSpace.xs), textAlign = TextAlign.Center)
}

/**
 * The header row of a full-screen route: a back button, the [title] centred, and room on the right
 * so it stays centred. [backLabel] says what going back does when it does more than leave.
 */
@Composable
fun RowScope.BackHeader(title: String, onBack: () -> Unit, backLabel: String = "Back") {
    ExIconButton(ExIcons.Back, backLabel, onBack)
    HeaderLabel(title)
    Spacer(Modifier.size(ExSize.iconButton))
}
