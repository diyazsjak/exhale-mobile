package app.exhale.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Exhale's own line icons: 24×24 grid, 1.8 stroke, round caps. Tint with `Icon(tint = ...)`.
 * Kept in code (no icon library) so the set stays small and consistent.
 */
object ExIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val CIRCLE = "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0"

    // Paths the home-screen widget draws too (`widget/WidgetArt.kt`), so they live here once.
    const val EMBER_PATH =
        "M12 3C12 7 6.5 9 6.5 14.5a5.5 5.5 0 0 0 11 0C17.5 11.5 15.6 9.8 14.6 8.6C14.4 10.4 13.6 11.4 12.6 11.6C13.4 8.6 12.4 5.4 12 3Z"

    val Ember = icon("ember", EMBER_PATH)
    val Regulate = icon("regulate", "M7 3h10", "M7 21h10", "M8 3c0 5 8 6 8 9s-8 4-8 9", "M16 3c0 5-8 6-8 9s8 4 8 9")
    val Rhythm = icon("rhythm", CIRCLE, "M12 7.5V12l3 2")
    val Air = icon("air", "M3 8.5h10a2.5 2.5 0 1 0-2.5-2.5", "M3 12.5h15a2.5 2.5 0 1 1-2.5 2.5", "M3 16.5h7")
    val Money = icon("money", CIRCLE, "M14.5 9.5c-.5-.9-1.4-1.3-2.5-1.3-1.5 0-2.5.8-2.5 1.9 0 2.6 5 1.4 5 4 0 1.1-1 1.9-2.5 1.9-1.1 0-2.1-.5-2.6-1.4", "M12 6.5v1.7", "M12 15.9v1.6")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Drag = icon("drag", "M5 9h14", "M5 15h14")
    val Eye = icon("eye", "M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12Z", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    val EyeOff = icon("eye-off", "M3 3l18 18", "M10.6 5.6A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a16 16 0 0 1-2.9 3.7", "M6.6 6.9A16 16 0 0 0 2.5 12s3.5 6.5 9.5 6.5c1.9 0 3.6-.6 5-1.5", "M9.9 9.9a3 3 0 0 0 4.2 4.2")
    val Minus = icon("minus", "M5 12h14")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Close = icon("close", "M6 6l12 12", "M18 6L6 18")
    val Back = icon("back", "M19 12H5", "M11 6l-6 6 6 6")
    const val FORWARD_PATH = "M9 6l6 6-6 6"
    val Forward = icon("forward", FORWARD_PATH)
    val Trash = icon("trash", "M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12", "M9 7V4h6v3")
    val Settings = icon("settings", "M4 7h9", "M17 7h3", "M4 17h3", "M11 17h9", "M15 5v4", "M9 15v4")
    const val UNDO_PATH = "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3"
    val Undo = icon("undo", UNDO_PATH)
    val Wave = icon("wave", "M2 14c2.5 0 3-6 5.5-6S10 16 12.5 16 15 8 17.5 8 20 14 22 14")
    val Sun = icon("sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 2.5v2", "M12 19.5v2", "M2.5 12h2", "M19.5 12h2", "M5.3 5.3l1.4 1.4", "M17.3 17.3l1.4 1.4", "M5.3 18.7l1.4-1.4", "M17.3 6.7l1.4-1.4")
    val Moon = icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z")
    val Palette = icon("palette", "M12 3a9 9 0 1 0 0 18c1.2 0 1.8-.8 1.8-1.7 0-1.2-1-1.6-1-2.6 0-1 .8-1.7 1.8-1.7H17a4 4 0 0 0 4-4C21 6.6 17 3 12 3Z", "M7.5 11.5h.01", "M10 7.5h.01", "M14.5 7.5h.01")
}
