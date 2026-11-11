package app.exhale.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
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
import app.exhale.R
import app.exhale.domain.WidgetKind
import app.exhale.domain.WidgetModel
import app.exhale.ui.theme.ExColors
import java.time.ZoneId

/**
 * The widget's layouts (SPEC §3.9). Square-ish sizes get the ring with one line under it; short
 * ones (the 4×1) a row: a small ring, the count and line, and the ember button. Tapping the ring or
 * the button arms, a second tap logs; anywhere else opens Exhale.
 *
 * Glance has its own text and colour types, so this reads [ExColors] values directly and sizes
 * text to match `ExType` (caption 13, number-small 15, label-small 14). Fonts are the system's:
 * only the count, drawn in [WidgetArt], is in Newsreader.
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
        modifier = root(c, 28.dp /* ExRadius.lg */).padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(WidgetArt.ring(context, m, c, ring, compact = false)),
            contentDescription = if (tappable) WidgetCopy.tapLabel(m, zone, h24) else null,
            modifier = GlanceModifier.size(ring.dp).let { if (tappable) it.clickable(actionRunCallback<WidgetTapAction>()) else it },
        )
        Box(modifier = GlanceModifier.height(44.dp), contentAlignment = Alignment.Center) {
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
    val button = (size.height.value - 20f).coerceIn(44f, 52f)
    Row(
        modifier = root(c, minOf(size.height / 2, 38.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(WidgetArt.ring(context, m, c, ring, compact = true)),
            contentDescription = null,
            modifier = GlanceModifier.size(ring.dp),
        )
        Spacer(GlanceModifier.width(12.dp))
        if (size.width >= 200.dp) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    WidgetCopy.title(m),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(if (m.overToday > 0) c.flareInk else c.ink),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    ),
                )
                LineText(WidgetCopy.line(m, wide = true, zone, h24), c)
            }
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.width(12.dp))
        when (m.kind) {
            WidgetKind.SETUP -> Box(modifier = GlanceModifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(R.drawable.ic_widget_forward, c.inkSoft, 24.dp)
            }
            WidgetKind.LOGGED -> UndoPill(c, height = 44.dp)
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
                    Icon(R.drawable.ic_widget_ember, if (armed) (if (c.isDark) c.bg else c.ink) else c.ink, 24.dp)
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
    TextStyle(color = ColorProvider(color), fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Undo for the log the widget just made: a `surfaceHigh` pill, in a 44dp touch target. */
@Composable
private fun UndoPill(c: ExColors, height: Dp) {
    Box(
        modifier = GlanceModifier.height(44.dp).clickable(actionRunCallback<WidgetUndoAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = GlanceModifier.height(height).background(c.surfaceHigh).cornerRadius(height / 2)
                .padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(R.drawable.ic_widget_undo, c.ink, 16.dp)
            Spacer(GlanceModifier.width(6.dp))
            Text("Undo", style = TextStyle(color = ColorProvider(c.ink), fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun Icon(res: Int, tint: Color, size: Dp) {
    Image(
        provider = ImageProvider(res),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(ColorProvider(tint)),
    )
}
