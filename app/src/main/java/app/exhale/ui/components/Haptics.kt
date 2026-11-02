package app.exhale.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The haptic vocabulary (see docs/SPEC.md §Haptics). Keep each meaning on one pattern.
 * - [logging]: rising buzz while holding to log
 * - [logged]: firm thud when a log commits
 * - [earned]: soft double tick when the interval is earned
 * - [breath]: slow pulse on breath phase change
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun composition(fallback: () -> VibrationEffect, build: VibrationEffect.Composition.() -> Unit) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 31) {
            val c = VibrationEffect.startComposition().apply(build)
            runCatching { v.vibrate(c.compose()) }.onFailure { v.vibrate(fallback()) }
        } else {
            v.vibrate(fallback())
        }
    }

    fun logging() = composition({ VibrationEffect.createOneShot(1400, 60) }) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.6f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 1f)
    }

    fun logged() = composition({ VibrationEffect.createOneShot(40, 255) }) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
    }

    fun earned() = composition({ VibrationEffect.createWaveform(longArrayOf(0, 20, 80, 20), -1) }) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 80)
    }

    fun breath() = composition({ VibrationEffect.createOneShot(30, 80) }) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.8f)
    }

    fun cancel() = vibrator?.cancel()
}

@Composable
fun rememberHaptics(): Haptics {
    val ctx = LocalContext.current
    return remember(ctx) { Haptics(ctx.applicationContext) }
}
