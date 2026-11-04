package app.exhale.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace

/** How far the leading drops when it changes. */
private val DROP = 16.dp

/** The new leading drops in over this long (the same as the page slide); the old one leaves faster. */
private const val ENTER_MS = 260
private const val EXIT_MS = 200

/**
 * The shared top bar of the tabs, drawn over them like the tab bar. [leading] fills the left and
 * changes with [key]: the new one drops in from above and fades in while the old one drops away and
 * fades out. The Settings button on the right never moves. The bar is solid `bg` with a short fade
 * ([ExSize.appBarFade]) below it, so content scrolls softly in beneath. Screens under it leave room
 * with `ExScreen(underAppBar = true)`.
 */
@Composable
fun <T> ExAppBar(
    key: T,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.(T) -> Unit,
) {
    val bg = Ex.colors.bg
    val drop = with(LocalDensity.current) { DROP.roundToPx() }
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val fade = ExSize.appBarFade.toPx()
                val solid = size.height - fade
                drawRect(bg, size = Size(size.width, solid))
                drawRect(
                    Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)), startY = solid, endY = size.height),
                    topLeft = Offset(0f, solid), size = Size(size.width, fade),
                )
            }
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = ExSpace.gutter)
                .padding(top = ExSpace.xs, bottom = ExSize.appBarFade)
                .fillMaxWidth()
                .heightIn(min = ExSize.iconButton),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = key,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (slideInVertically(tween(ENTER_MS, easing = LinearOutSlowInEasing)) { -drop } + fadeIn(tween(ENTER_MS))) togetherWith
                        (slideOutVertically(tween(EXIT_MS, easing = FastOutSlowInEasing)) { drop } + fadeOut(tween(EXIT_MS))) using
                        SizeTransform(clip = false)
                },
                contentAlignment = Alignment.CenterStart,
                label = "app-bar-leading",
            ) { k ->
                Row(Modifier.fillMaxWidth().padding(end = ExSpace.xs), verticalAlignment = Alignment.CenterVertically) { leading(k) }
            }
            ExIconButton(ExIcons.Settings, "Settings", onSettings)
        }
    }
}
