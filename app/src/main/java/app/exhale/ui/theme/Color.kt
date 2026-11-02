package app.exhale.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Colour tokens. See docs/DESIGN-SYSTEM.md.
 *
 * Neutrals carry the UI. Accents carry meaning ("two temperatures"):
 *   ember  = a cigarette, a log           flare = craving, over allowance
 *   sky    = transition hot → cool        oxy   = recovery, earned time
 *   sun    = money, milestones
 * Never use a warm accent for success or a cool accent for smoking.
 * Accents are fills and graphics. For text in an accent use the matching `*Ink` (or `inkOf`):
 * in dark they are identical, in light they are darker so text meets 4.5:1.
 */
@Immutable
data class ExColors(
    val isDark: Boolean,
    /** The one background every screen uses. */
    val bg: Color,
    /** Cards, chips, inputs, tab bar. */
    val surface: Color,
    /** Pressed / selected surface, inner highlight in the tab bar. */
    val surfaceHigh: Color,
    val line: Color,
    /** Floating navigation (tab bar): a step apart from cards so the two never merge. */
    val chrome: Color,
    val chromeSelected: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    /** Primary button fill and its label: inverted ink, like the white buttons on black. */
    val primary: Color,
    val onPrimary: Color,
    val ember: Color,
    val flare: Color,
    val sky: Color,
    val oxy: Color,
    val sun: Color,
    /** Text versions of the accents: same hue, dark enough for 4.5:1 text on bg and surfaces. */
    val emberInk: Color,
    val flareInk: Color,
    val skyInk: Color,
    val oxyInk: Color,
    val sunInk: Color,
    val scrim: Color,
) {
    /** The text-safe version of an accent. Use for any text or small icon drawn in an accent. */
    fun inkOf(accent: Color): Color = when (accent) {
        ember -> emberInk
        flare -> flareInk
        sky -> skyInk
        oxy -> oxyInk
        sun -> sunInk
        else -> accent
    }

    /** heat 1 = just smoked (ember), 0.5 = sky, 0 = earned (oxy). */
    fun heat(h: Float): Color = if (h >= 0.5f) lerp(sky, ember, (h - 0.5f) * 2f) else lerp(oxy, sky, h * 2f)

    /** Secondary hue used next to [heat] in gradients (rose on the hot side). */
    fun heatAccent(h: Float): Color = if (h >= 0.5f) lerp(sky, flare, (h - 0.5f) * 2f) else lerp(oxy, sky, h * 2f)

    /** [heat] for icons and text: the same scale built from the ink variants. */
    fun heatInk(h: Float): Color = if (h >= 0.5f) lerp(skyInk, emberInk, (h - 0.5f) * 2f) else lerp(oxyInk, skyInk, h * 2f)

    /** Soft tinted fill for selected chips and badges. */
    fun tint(accent: Color): Color = accent.copy(alpha = if (isDark) 0.18f else 0.14f)
}

val DarkColors = ExColors(
    isDark = true,
    bg = Color(0xFF0C0C10),
    surface = Color(0xFF18181E),
    surfaceHigh = Color(0xFF26262E),
    line = Color(0xFF2E2E37),
    chrome = Color(0xFF222229),
    chromeSelected = Color(0xFF34343E),
    ink = Color(0xFFF4F3F7),
    inkSoft = Color(0xFFA6A4B0),
    inkFaint = Color(0xFF8A8894),
    primary = Color(0xFFF4F3F7),
    onPrimary = Color(0xFF0C0C10),
    ember = Color(0xFFFFA285),
    flare = Color(0xFFF59DC3),
    sky = Color(0xFF8FA2FF),
    oxy = Color(0xFF86E3B6),
    sun = Color(0xFFF3D27A),
    emberInk = Color(0xFFFFA285),
    flareInk = Color(0xFFF59DC3),
    skyInk = Color(0xFF8FA2FF),
    oxyInk = Color(0xFF86E3B6),
    sunInk = Color(0xFFF3D27A),
    scrim = Color(0x99000000),
)

val LightColors = ExColors(
    isDark = false,
    bg = Color(0xFFF3F3F6),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE7E7EC),
    line = Color(0xFFDCDBE2),
    chrome = Color(0xFFFFFFFF),
    chromeSelected = Color(0xFFE9E9EE),
    ink = Color(0xFF131318),
    inkSoft = Color(0xFF5C5A66),
    inkFaint = Color(0xFF6F6D79),
    primary = Color(0xFF131318),
    onPrimary = Color(0xFFF3F3F6),
    ember = Color(0xFFE97E5A),
    flare = Color(0xFFD9689A),
    sky = Color(0xFF5B6FE6),
    oxy = Color(0xFF2EA676),
    sun = Color(0xFFC9972A),
    emberInk = Color(0xFFAC4527),
    flareInk = Color(0xFFB03C72),
    skyInk = Color(0xFF4253CC),
    oxyInk = Color(0xFF187550),
    sunInk = Color(0xFF86600F),
    scrim = Color(0x66000000),
)
