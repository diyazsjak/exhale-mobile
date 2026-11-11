package app.exhale.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.exhale.MainActivity
import app.exhale.ui.components.ExIcons
import app.exhale.domain.WidgetKind
import app.exhale.domain.WidgetModel
import app.exhale.ui.theme.ExColors
import app.exhale.ui.theme.ExRadius
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.time.ZoneId

/**
 * The widget's layouts (SPEC §3.9). Square-ish sizes get the ring with one line under it; short
 * ones (the 4×1) a row: a small ring, the count and line, and the ember button. Tapping the ring or
 * the button arms, a second tap logs; anywhere else opens Exhale.
 *
 * Glance has its own text and colour types, so this reads [ExColors] values directly and takes text
 * sizes from `ExType`. Fonts are the system's: only the count, drawn in [WidgetArt], is in Newsreader.
 */
@Composable
fun WidgetContent(m: WidgetModel, c: ExColors, zone: ZoneId, h24: Boolean) {
    val size = LocalSize.current
    if (size.height < 120.dp) WideLayout(m, c, zone, h24) else SquareLayout(m, c, zone, h24)
}

/** The widget's card: anywhere that isn't the ring, the button or Undo opens Exhale. */
private fun root(c: ExColors, radius: Dp): GlanceModifier =
    GlanceModifier.fillMaxSize().appWidgetBackground().background(c.surface).cornerRadius(radius)
        .clickable(actionStartActivity<MainActivity>())

@Composable
private fun SquareLayout(m: WidgetModel, c: ExColors, zone: ZoneId, h24: Boolean) {
    val context = LocalContext.current
    val size = LocalSize.current
    val ring = minOf(size.width.value - 16f, size.height.value - 60f).coerceIn(64f, 132f)
    val tappable = m.kind != WidgetKind.SETUP && m.kind != WidgetKind.LOGGED
    Column(
        modifier = root(c, ExRadius.LG).padding(top = ExSpace.xs, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(WidgetArt.ring(context, m, c, ring, compact = false)),
            contentDescription = if (tappable) WidgetCopy.tapLabel(m, zone, h24) else null,
            modifier = GlanceModifier.size(ring.dp).let { if (tappable) it.clickable(actionRunCallback<WidgetTapAction>()) else it },
        )
        Box(modifier = GlanceModifier.height(ExSize.touch), contentAlignment = Alignment.Center) {
            if (m.kind == WidgetKind.LOGGED) UndoPill(c, height = 32.dp)
            else LineText(WidgetCopy.line(m, wide = false, zone, h24), c)
        }
    }
}

@Composable
private fun WideLayout(m: WidgetModel, c: ExColors, zone: ZoneId, h24: Boolean) {
    val context = LocalContext.current
    val size = LocalSize.current
    val ring = (size.height.value - 20f).coerceIn(36f, 52f)
    val button = (size.height.value - 20f).coerceIn(ExSize.touch.value, ExSize.row.value)
    Row(
        modifier = root(c, minOf(size.height / 2, 38.dp)).padding(horizontal = ExSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(WidgetArt.ring(context, m, c, ring, compact = true)),
            contentDescription = null,
            modifier = GlanceModifier.size(ring.dp),
        )
        Spacer(GlanceModifier.width(ExSpace.sm))
        if (size.width >= 200.dp) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    WidgetCopy.title(m),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(if (m.overToday > 0) c.flareInk else c.ink),
                        fontSize = ExType.numberSmall.fontSize, fontWeight = FontWeight.Medium,
                    ),
                )
                LineText(WidgetCopy.line(m, wide = true, zone, h24), c)
            }
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.width(ExSpace.sm))
        when (m.kind) {
            WidgetKind.SETUP -> Box(modifier = GlanceModifier.size(ExSize.touch), contentAlignment = Alignment.Center) {
                Icon(ExIcons.FORWARD_PATH, c.inkSoft, ExSize.icon)
            }
            WidgetKind.LOGGED -> UndoPill(c, height = ExSize.touch)
            else -> {
                val armed = m.kind == WidgetKind.ARMED
                Box(
                    modifier = GlanceModifier.size(button.dp)
                        .background(if (armed) c.ember else c.surfaceHigh)
                        .cornerRadius(button.dp / 2)
                        .clickable(actionRunCallback<WidgetTapAction>())
                        .semantics { contentDescription = WidgetCopy.tapLabel(m, zone, h24) },
                    contentAlignment = Alignment.Center,
                ) {
                    // On the ember fill the icon is near-black in both themes (like on-primary in dark).
                    Icon(ExIcons.EMBER_PATH, if (armed && c.isDark) c.bg else c.ink, ExSize.icon)
                }
            }
        }
    }
}

@Composable
private fun LineText(line: WidgetCopy.Line, c: ExColors) {
    val leadColor = when (line.tone) {
        WidgetCopy.Tone.SOFT -> c.inkSoft
        WidgetCopy.Tone.STRONG -> c.ink
        WidgetCopy.Tone.OXY -> c.oxyInk
        WidgetCopy.Tone.FLARE -> c.flareInk
    }
    val bold = line.tone == WidgetCopy.Tone.STRONG
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(line.lead, maxLines = 1, style = caption(leadColor, bold))
        if (line.strong.isNotEmpty()) Text(" " + line.strong, maxLines = 1, style = caption(c.ink, bold = true))
        if (line.tail.isNotEmpty()) Text(line.tail, maxLines = 1, style = caption(c.inkSoft, bold = false))
    }
}

private fun caption(color: Color, bold: Boolean) =
    TextStyle(color = ColorProvider(color), fontSize = ExType.caption.fontSize, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Undo for the log the widget just made: a `surfaceHigh` pill, in a 44dp touch target. */
@Composable
private fun UndoPill(c: ExColors, height: Dp) {
    Box(
        modifier = GlanceModifier.height(ExSize.touch).clickable(actionRunCallback<WidgetUndoAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = GlanceModifier.height(height).background(c.surfaceHigh).cornerRadius(height / 2)
                .padding(start = ExSpace.sm, end = ExSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ExIcons.UNDO_PATH, c.ink, ExSize.iconSmall)
            Spacer(GlanceModifier.width(6.dp))
            Text("Undo", style = TextStyle(color = ColorProvider(c.ink), fontSize = ExType.labelSmall.fontSize, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun Icon(pathData: String, tint: Color, size: Dp) {
    Image(
        provider = ImageProvider(WidgetArt.icon(LocalContext.current, pathData, tint, size)),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}
