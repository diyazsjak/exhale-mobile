package app.exhale.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * The haptic vocabulary (see docs/DESIGN-SYSTEM.md, Motion and haptics). One meaning per pattern:
 * - [charge]: short ticks of rising strength while holding the ember
 * - [logged]: firm thud when a log commits
 * - [earned]: soft double tick when the interval is earned
 * - [breath]: low tick on a breath phase change
 *
 * Many phones don't support composition primitives (most Samsung and Xiaomi models, and some
 * primitives on Pixels); asking for one there vibrates nothing, silently. So every pattern checks
 * support first, falls back to plain one-shots, and finally to the view's haptic feedback.
 */
class Haptics(context: Context, private val view: View?) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val hasMotor = vibrator?.hasVibrator() == true
    private val amplitude = hasMotor && vibrator?.hasAmplitudeControl() == true

    private fun supported(primitive: Int): Boolean =
        hasMotor && Build.VERSION.SDK_INT >= 31 && vibrator!!.areAllPrimitivesSupported(primitive)

    private fun primitive(primitive: Int, scale: Float, delayMs: Int = 0, repeat: Int = 1) {
        if (Build.VERSION.SDK_INT < 31) return
        val c = VibrationEffect.startComposition()
        repeat(repeat) { i -> c.addPrimitive(primitive, scale.coerceIn(0f, 1f), if (i == 0) 0 else delayMs) }
        vibrator!!.vibrate(c.compose())
    }

    /** Plain vibration: [ms] long at [strength] 0..1 (default strength without amplitude control). */
    private fun oneShot(ms: Long, strength: Float) {
        val amp = if (amplitude) (strength.coerceIn(0f, 1f) * 254 + 1).toInt() else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator!!.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    private val tick = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_TICK else -1
    private val lowTick = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else -1
    private val thud = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_THUD else -1
    private val click = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_CLICK else -1

    /** One step of the hold-to-log charge. [level] 0..1 rises as the hold completes. */
    fun charge(level: Float) = when {
        supported(tick) -> primitive(tick, 0.25f + 0.75f * level)
        hasMotor -> oneShot(8 + (10 * level).toLong(), 0.2f + 0.6f * level)
        else -> { view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); Unit }
    }

    fun logged() = when {
        supported(thud) -> primitive(thud, 1f)
        supported(click) -> primitive(click, 1f)
        hasMotor -> oneShot(45, 1f)
        else -> { view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); Unit }
    }

    fun earned() = when {
        supported(tick) -> primitive(tick, 0.6f, delayMs = 80, repeat = 2)
        amplitude -> vibrator!!.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 18, 80, 18), intArrayOf(0, 150, 0, 150), -1))
        hasMotor -> vibrator!!.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 18, 80, 18), -1))
        else -> { view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); Unit }
    }

    fun breath() = when {
        supported(lowTick) -> primitive(lowTick, 0.8f)
        supported(tick) -> primitive(tick, 0.5f)
        hasMotor -> oneShot(20, 0.35f)
        else -> { view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); Unit }
    }

    fun cancel() = vibrator?.cancel()
}

@Composable
fun rememberHaptics(): Haptics {
    val ctx = LocalContext.current
    val view = LocalView.current
    return remember(ctx, view) { Haptics(ctx.applicationContext, view) }
}
