package app.exhale.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import kotlinx.coroutines.delay

/** What an [ExToast] shows. Call [show] for each note; the newest replaces the one on screen. */
@Stable
class ExToastState(initial: String? = null) {
    var message by mutableStateOf(initial)
        private set
    internal var shown by mutableIntStateOf(if (initial != null) 1 else 0)
        private set

    fun show(text: String) {
        message = text
        shown++
    }
}

@Composable
fun rememberToastState(): ExToastState = remember { ExToastState() }

/**
 * A short note that rises from the bottom for about two seconds after something saved quietly,
 * e.g. "Saved" when going back from an edit screen. `chrome` pill with a hairline, a check in
 * oxygen ink. [bottom] lifts it above a bottom bar.
 */
@Composable
fun ExToast(state: ExToastState, modifier: Modifier = Modifier, bottom: Dp = ExSpace.xl) {
    val c = Ex.colors
    var visible by remember { mutableStateOf(state.shown > 0) }
    LaunchedEffect(state.shown) {
        if (state.shown == 0) return@LaunchedEffect
        visible = true
        delay(2200)
        visible = false
    }
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = bottom), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Row(
                Modifier
                    .padding(horizontal = ExSpace.gutter)
                    .clip(ExRadius.pill)
                    .background(c.chrome)
                    .border(ExSize.hairline, c.line, ExRadius.pill)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(ExSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ExIcons.Check, null, Modifier.size(18.dp), tint = c.oxyInk)
                Text(state.message.orEmpty(), style = ExType.labelSmall, color = c.ink)
            }
        }
    }
}

/**
 * A small centred card over a scrim, for something that didn't fit where it was, such as a long
 * goal name. Tap outside, press Back or the button to close.
 */
@Composable
fun ExDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier, action: String = "Close", content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ExCard(modifier) {
            content()
            ExButton(
                action, onDismiss,
                Modifier.fillMaxWidth().padding(top = ExSpace.sm),
                style = ButtonStyle.Tonal, size = ButtonSize.Medium,
            )
        }
    }
}

/** One button in an [ExChoiceDialog] or [ExInputDialog]. */
data class DialogAction(val label: String, val style: ButtonStyle, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * A question before something that can't be undone or would be lost: a serif title, a line on
 * what happens, then the choices stacked full width (the main one first, as Primary). Tapping
 * outside or Back is [onDismiss], the same as the safe choice.
 */
@Composable
fun ExChoiceDialog(title: String, text: String, actions: List<DialogAction>, onDismiss: () -> Unit) {
    val c = Ex.colors
    Dialog(onDismissRequest = onDismiss) {
        ExDialogCard(title, actions) { Text(text, style = ExType.body, color = c.inkSoft) }
    }
}

/**
 * A dialog for typing something short, such as a new trigger's name: a serif title, the field(s), then
 * the choices stacked full width. A modal like [ExChoiceDialog]; the window moves up for the keyboard,
 * so the field stays in view. Tapping outside or Back is [onDismiss].
 */
@Composable
fun ExInputDialog(title: String, onDismiss: () -> Unit, actions: List<DialogAction>, content: @Composable ColumnScope.() -> Unit) {
    // The same modal as ExChoiceDialog: the platform's scrim, and the window moves up for the keyboard.
    Dialog(onDismissRequest = onDismiss) { ExDialogCard(title, actions, content = content) }
}

/**
 * The card inside the kit's dialogs, without the dialog window: `space-xl` padding, a `headline`
 * [title] (or none, when [content] brings its own header), [content], then [actions] stacked full width. The dialogs wrap it in `Dialog`; the
 * catalogue and screenshots draw it in place (a dialog window never goes idle in the test harness).
 * [color] `surface-high` lifts `surface` chips off it.
 */
@Composable
fun ExDialogCard(
    title: String?,
    actions: List<DialogAction>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExCard(modifier, color = color, padding = ExSpace.xl) {
        if (title != null) Text(title, style = ExType.headline, color = Ex.colors.ink)
        content()
        DialogActions(actions)
    }
}

/** A dialog's choices, stacked full width as Medium buttons. */
@Composable
private fun DialogActions(actions: List<DialogAction>) {
    Column(Modifier.padding(top = ExSpace.sm), verticalArrangement = Arrangement.spacedBy(ExSpace.xs)) {
        actions.forEach { ExButton(it.label, it.onClick, Modifier.fillMaxWidth(), style = it.style, size = ButtonSize.Medium, enabled = it.enabled) }
    }
}
