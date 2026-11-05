package app.exhale.domain

import kotlin.math.ceil
import kotlin.math.cos

/** Length of one craving wave in seconds. Research: most urges pass within 3–5 minutes. */
const val WAVE_SECONDS = 240f

/** Paced breathing: in 4, hold 2, out 6. A longer out-breath calms the body. */
enum class BreathPhase(val verb: String, val seconds: Float) {
    IN("Breathe in", 4f),
    HOLD("Hold", 2f),
    OUT("Breathe out", 6f);

    val next: BreathPhase get() = entries[(ordinal + 1) % entries.size]
}

/** Where the breath is at time t: phase, progress through it 0..1, whole seconds left. */
data class BreathState(val phase: BreathPhase, val progress: Float, val secondsLeft: Int) {
    /** How full the lungs are, 0..1, eased like a real breath. Drives the wave dot's halo. */
    val fullness: Float
        get() = when (phase) {
            BreathPhase.IN -> easeInOutSine(progress)
            BreathPhase.HOLD -> 1f
            BreathPhase.OUT -> 1f - easeInOutSine(progress)
        }
}

private fun easeInOutSine(x: Float) = ((1 - cos(Math.PI * x)) / 2).toFloat()

private val CYCLE = BreathPhase.entries.sumOf { it.seconds.toDouble() }.toFloat()

/** The breath [t] seconds into the wave; the 4-2-6 cycle repeats from the start. */
fun breathAt(t: Float): BreathState {
    var x = t % CYCLE
    for (p in BreathPhase.entries) {
        if (x < p.seconds) return BreathState(p, x / p.seconds, ceil(p.seconds - x).toInt().coerceAtLeast(1))
        x -= p.seconds
    }
    return BreathState(BreathPhase.OUT, 1f, 1)
}
