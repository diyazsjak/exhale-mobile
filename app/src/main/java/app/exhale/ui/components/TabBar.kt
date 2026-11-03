package app.exhale.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace

/** How far above the tab bar the darkening scrim starts. */
private val SCRIM_REACH = 88.dp

data class TabItem(val icon: ImageVector, val label: String, val tint: Color? = null)

/**
 * iOS-style floating tab bar: a pill hovering above the content, icons only, the selected
 * tab sits in a lighter inner pill. [TabItem.tint] colours the selected icon (e.g. heat for Ember).
 * Place it at the bottom of a Box; it adds the navigation-bar inset itself. Content scrolls
 * underneath it and darkens gradually toward the bar. Light: a soft elevation shadow. Dark: a
 * lighter surface and hairline outline.
 */
@Composable
fun FloatingTabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ex.colors
    // Content scrolls underneath the solid bar. A soft scrim darkens it gradually from
    // SCRIM_REACH above the bar down to the screen edge so the bar always stands out.
    val scrim = if (c.isDark) Color.Black.copy(alpha = 0.4f) else c.ink.copy(alpha = 0.12f)
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to scrim.copy(alpha = 0f),
                        0.35f to scrim.copy(alpha = scrim.alpha * 0.25f),
                        0.7f to scrim.copy(alpha = scrim.alpha * 0.75f),
                        1f to scrim,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = ExSpace.gutter, end = ExSpace.gutter, top = SCRIM_REACH, bottom = ExSpace.sm),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ExSize.tabBar)
                    // Light: a soft platform shadow. Dark: no shadow (it doesn't read on dark); the bar is
                    // lifted by its lighter tone and hairline outline instead (tonal elevation).
                    .then(
                        if (c.isDark) Modifier
                        else Modifier.shadow(10.dp, ExRadius.pill, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
                    )
                    .clip(ExRadius.pill)
                    .background(c.chrome)
                    .border(1.dp, if (c.isDark) c.line else c.line.copy(alpha = 0.6f), ExRadius.pill)
                    .padding(6.dp)
                    .selectableGroup(),
            ) {
                items.forEachIndexed { i, item ->
                    val sel = i == selected
                    val bg by animateColorAsState(if (sel) c.chromeSelected else Color.Transparent, label = "tab-bg")
                    val fg by animateColorAsState(if (sel) item.tint?.let { c.inkOf(it) } ?: c.ink else c.inkSoft, label = "tab-fg")
                    val source = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(pressScale(source))
                            .clip(ExRadius.pill)
                            .background(bg)
                            .selectable(sel, source, null, role = Role.Tab) { onSelect(i) }
                            .semantics { contentDescription = item.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, null, Modifier.size(ExSize.icon), tint = fg)
                    }
                }
            }
        }
    }
}

