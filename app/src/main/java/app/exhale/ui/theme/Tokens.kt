package app.exhale.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 4-pt spacing scale. Use these instead of raw dp in screens. */
object ExSpace {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Side gutter for every screen. */
    val gutter = 20.dp
}

object ExRadius {
    /** Small drawn objects, e.g. the pack icons on Money. */
    val xs = RoundedCornerShape(6.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(20.dp)
    val lg = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)
}

/** Fixed sizes shared by components. */
object ExSize {
    val buttonLarge = 60.dp
    val buttonMedium = 48.dp
    val iconButton = 48.dp
    val chip = 40.dp
    val tabBar = 68.dp
    val icon = 24.dp

    /** The soft fade under the shared app bar, where content scrolls in beneath it. */
    val appBarFade = 16.dp

    /** Space screens leave above the navigation-bar inset so content clears the floating tab bar. */
    val tabBarClearance = 100.dp
}
