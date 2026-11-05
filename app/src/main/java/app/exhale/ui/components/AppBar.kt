package app.exhale.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace

/** How far the leading drops when it changes. */
private val DROP = 16.dp

/** The new leading drops in over this long (the same as the page slide); the old one leaves faster. */
private const val ENTER_MS = 260
private const val EXIT_MS = 200

/** The scrolled-under border fades in and out this quickly. */
private const val BORDER_MS = 150

/** A part-hidden bar settles to fully shown or hidden like this after a scroll ends. */
private val SETTLE: AnimationSpec<Float> = tween(200, easing = FastOutSlowInEasing)

/**
 * "Enter always" scrolling for [ExAppBar], as in Material 3: the bar slides up out of view as soon
 * as the content scrolls up, comes back as soon as it scrolls down, and settles fully shown or
 * fully hidden when the scroll ends. It follows only the scroll the content actually made, so
 * pulling at the top of a list doesn't hide it. Attach it with `Modifier.nestedScroll(it)` around
 * the scrolling screens; [show] brings the bar back (e.g. on a tab change).
 */
@Stable
class AppBarScrollBehavior : NestedScrollConnection {
    /** The bar's own height in px (status bar not included), set by [ExAppBar]. */
    internal var heightPx by mutableFloatStateOf(0f)

    /** How much of the bar is hidden, 0 (shown) to [heightPx]. */
    internal var hiddenPx by mutableFloatStateOf(0f)

    /** While set, the bar stays put until the scroll position passes [holdUntil] (see [holdWhile]). */
    private var position: (() -> Float)? = null
    private var holdUntil: () -> Float = { 0f }

    fun show() { hiddenPx = 0f }

    /**
     * Keep the bar in place while the scroll position ([position], px) is under [until] (px), then
     * let it go up 1:1 with the content beyond that, so it leaves together with something held
     * under it (Air's hero). Scrolling back down still brings it back at once.
     */
    fun holdWhile(position: () -> Float, until: () -> Float) {
        this.position = position
        holdUntil = until
    }

    fun clearHold() { position = null }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val pos = position
        val delta = if (consumed.y < 0f && pos != null) {
            // Scrolling on: only the part of this step beyond the held stretch hides the bar.
            val after = pos()
            val before = after + consumed.y
            -(after - maxOf(before, holdUntil())).coerceAtLeast(0f)
        } else consumed.y
        hiddenPx = (hiddenPx - delta).coerceIn(0f, heightPx)
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (hiddenPx > 0f && hiddenPx < heightPx) {
            val target = if (hiddenPx > heightPx / 2f) heightPx else 0f
            animate(hiddenPx, target, animationSpec = SETTLE) { v, _ -> hiddenPx = v }
        }
        return Velocity.Zero
    }
}

@Composable
fun rememberAppBarScrollBehavior(): AppBarScrollBehavior = remember { AppBarScrollBehavior() }

/**
 * The scrolled-under border colour of a bar (the app bar, an [ExScreen] header): `line` that fades in
 * while content is [under] the bar (Material 3's scrolled state). Draw it with [bottomHairline].
 */
@Composable
internal fun scrolledBorderColor(under: Boolean): State<Color> {
    val line = Ex.colors.line
    return animateColorAsState(if (under) line else line.copy(alpha = 0f), tween(BORDER_MS), label = "bar-border")
}

/** A hairline along the bottom edge in [color], read while drawing so an animated colour only redraws. */
internal fun Modifier.bottomHairline(color: () -> Color): Modifier = drawBehind {
    val w = ExSize.hairline.toPx()
    drawLine(color(), Offset(0f, size.height - w / 2f), Offset(size.width, size.height - w / 2f), w)
}

/** A bar's content row: full width, gutter at the sides, 8 + 48 + 8 = 64dp tall. For the app bar and [ExScreen] headers. */
internal fun Modifier.barRow(): Modifier =
    fillMaxWidth().padding(horizontal = ExSpace.gutter).padding(vertical = ExSpace.xs).heightIn(min = ExSize.iconButton)

/**
 * The shared top bar of the tabs, drawn over them like the tab bar. [leading] fills the left and
 * changes with [key]: the new one drops in from above and fades in while the old one drops away and
 * fades out. The Settings button on the right never moves.
 *
 * Solid `bg`. While content is [scrolled] under it, a hairline `line` border shows along its bottom
 * (Material 3's scrolled state). With a [scrollBehavior] it slides away as the content scrolls up
 * and comes back as it scrolls down; the status bar strip above it stays. Screens under it leave
 * room with `ExScreen(underAppBar = true)`.
 */
@Composable
fun <T> ExAppBar(
    key: T,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrolled: Boolean = false,
    scrollBehavior: AppBarScrollBehavior? = null,
    leading: @Composable RowScope.(T) -> Unit,
) {
    val bg = Ex.colors.bg
    val border = scrolledBorderColor(scrolled)
    val drop = with(LocalDensity.current) { DROP.roundToPx() }
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .onSizeChanged { scrollBehavior?.heightPx = it.height.toFloat() }
                .graphicsLayer { translationY = -(scrollBehavior?.hiddenPx ?: 0f) }
                .background(bg)
                .bottomHairline { border.value }
        ) {
            Row(
                Modifier.barRow(),
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
        // The status bar strip stays put; the bar slides up behind it.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(bg))
    }
}
