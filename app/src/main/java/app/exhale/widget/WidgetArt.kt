package app.exhale.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import app.exhale.R
import app.exhale.domain.WidgetKind
import app.exhale.domain.WidgetModel
import app.exhale.ui.components.ExIcons
import app.exhale.ui.theme.ExColors
import kotlin.math.roundToInt

/**
 * The widget's ring with the count inside, drawn as one bitmap: Glance can't draw arcs or use the
 * app's fonts, and the count is the one place that should be in Newsreader.
 *
 * Like Ember's countdown ring: a `surfaceHigh` track that fills sky → oxygen as the gap is earned,
 * a full oxygen ring with a soft glow once earned, and a core tinted by heat. While armed the ring
 * runs warm (ember → flare), like the hold's charge.
 */
object WidgetArt {
    /** Size the square layout is designed at; everything scales from it. */
    private const val SQUARE_DP = 120f
    private const val COMPACT_DP = 52f

    fun ring(context: Context, m: WidgetModel, c: ExColors, sizeDp: Float, compact: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val unit = px / if (compact) COMPACT_DP else SQUARE_DP // one design dp, in pixels
        val pad = (if (compact) 4f else 8f) * unit // room for the glow
        val stroke = (if (compact) 4f else 6f) * unit
        val cx = px / 2f
        val r = px / 2f - pad - stroke / 2f
        val oval = RectF(cx - r, cx - r, cx + r, cx + r)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        fun glow(color: Color, alpha: Float) {
            canvas.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke * 1.4f
                this.color = color.copy(alpha = alpha).toArgb()
                maskFilter = BlurMaskFilter(pad * 0.9f, BlurMaskFilter.Blur.NORMAL)
            })
        }

        when {
            m.kind == WidgetKind.SETUP -> canvas.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * unit
                color = c.line.toArgb()
                pathEffect = DashPathEffect(floatArrayOf(5f * unit, 4f * unit), 0f)
            })
            m.kind == WidgetKind.ARMED -> {
                glow(c.ember, 0.45f)
                ringPaint.shader = SweepGradient(cx, cx, intArrayOf(c.ember.toArgb(), c.flare.toArgb(), c.ember.toArgb()), null)
                canvas.drawCircle(cx, cx, r, ringPaint)
            }
            m.ringFill >= 1f -> {
                if (m.kind == WidgetKind.EARNED) glow(c.oxy, if (c.isDark) 0.35f else 0.3f)
                ringPaint.color = c.oxy.toArgb()
                canvas.drawCircle(cx, cx, r, ringPaint)
            }
            else -> {
                ringPaint.color = c.surfaceHigh.toArgb()
                canvas.drawCircle(cx, cx, r, ringPaint)
                if (m.ringFill > 0f) {
                    // The round cap pokes out before 12 o'clock: start the sweep that much earlier.
                    val capDeg = Math.toDegrees((stroke / 2f / r).toDouble()).toFloat()
                    val sweep = 360f * m.ringFill
                    ringPaint.shader = SweepGradient(
                        cx, cx, intArrayOf(c.sky.toArgb(), c.oxy.toArgb()),
                        floatArrayOf(0f, ((sweep + capDeg) / 360f).coerceAtMost(1f)),
                    ).apply { setLocalMatrix(Matrix().apply { setRotate(-90f - capDeg, cx, cx) }) }
                    canvas.drawArc(oval, -90f, sweep, false, ringPaint)
                }
            }
        }

        // The core: a faint wash of heat, a little stronger right after a log and while armed.
        val coreAlpha = when (m.kind) {
            WidgetKind.SETUP -> 0f
            WidgetKind.ARMED -> 0.2f
            WidgetKind.LOGGED -> 0.24f
            else -> if (c.isDark) 0.1f else 0.07f
        }
        if (coreAlpha > 0f) {
            val heat = c.heat(m.heat)
            val core = r - stroke / 2f - unit
            canvas.drawCircle(cx, cx, core, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cx, core,
                    intArrayOf(heat.copy(alpha = coreAlpha).toArgb(), heat.copy(alpha = 0f).toArgb()),
                    floatArrayOf(0f, 0.6f), Shader.TileMode.CLAMP,
                )
            })
        }

        if (m.kind == WidgetKind.SETUP) drawIcon(canvas, ExIcons.EMBER_PATH, cx, (if (compact) 20f else 30f) * unit, c.inkSoft)
        else drawCount(context, canvas, m, c, cx, unit, compact)
        return bmp
    }

    private fun drawCount(context: Context, canvas: Canvas, m: WidgetModel, c: ExColors, cx: Float, unit: Float, compact: Boolean) {
        val over = m.overToday > 0
        val serif = ResourcesCompat.getFont(context, R.font.newsreader) ?: Typeface.SERIF
        val count = m.count.toString()
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (compact && Build.VERSION.SDK_INT >= 28) Typeface.create(serif, 500, false) else serif
            textSize = (if (compact) 22f else 42f) * unit
            color = (if (over) c.flareInk else c.ink).toArgb()
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
            letterSpacing = if (compact) 0f else -0.01f
        }
        val countBox = Rect().also { countPaint.getTextBounds(count, 0, count.length, it) }
        if (compact) {
            canvas.drawText(count, cx, cx + countBox.height() / 2f - countBox.bottom, countPaint)
            return
        }
        val of = "of ${m.allowance}"
        val ofPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.figtree) ?: Typeface.SANS_SERIF
            textSize = 12f * unit
            color = (if (over) c.flareInk else c.inkSoft).toArgb()
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
        }
        val ofBox = Rect().also { ofPaint.getTextBounds(of, 0, of.length, it) }
        val gap = 6f * unit
        val top = cx - (countBox.height() + gap + ofBox.height()) / 2f
        canvas.drawText(count, cx, top - countBox.top, countPaint)
        canvas.drawText(of, cx, top + countBox.height() + gap - ofBox.top, ofPaint)
    }

    /** A [color] pill [width] × [height] (a circle when they match), for backgrounds Glance can't round before Android 12. */
    fun pill(context: Context, color: Color, width: Dp, height: Dp): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (width.value * density).roundToInt().coerceAtLeast(1)
        val h = (height.value * density).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
        }
    }

    /** An [ExIcons] line icon ([pathData]) in [tint], [sizeDp] across: Glance has no vector tinting of its own. */
    fun icon(context: Context, pathData: String, tint: Color, sizeDp: Dp): Bitmap {
        val px = (sizeDp.value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
            drawIcon(Canvas(it), pathData, px / 2f, px.toFloat(), tint)
        }
    }

    /** An [ExIcons] line icon, [size] across, centred on ([cx], [cx]), with the set's 1.8 stroke and round ends. */
    private fun drawIcon(canvas: Canvas, pathData: String, cx: Float, size: Float, color: Color) {
        val path = PathParser.createPathFromPathData(pathData)
        val k = size / 24f
        path.transform(Matrix().apply { setScale(k, k); postTranslate(cx - size / 2f, cx - size / 2f) })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * k
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color.toArgb()
        })
    }
}
