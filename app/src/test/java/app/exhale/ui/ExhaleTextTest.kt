package app.exhale.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.ui.components.ExhaleText
import app.exhale.ui.components.exhalePairs
import app.exhale.ui.components.exhaleTokens
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import app.exhale.ui.theme.ExhaleTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** The hero number fits the screen and follows the font size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ExhaleTextTest {
    private companion object {
        /** Height of each half in the two-hero test: room for the drift and blur, a whole number of pixels at 3x. */
        const val HALF_DP = 200
    }

    @get:Rule val compose = createComposeRule()

    private var fontScale by mutableFloatStateOf(1f)

    private fun hero(width: Int, size: Int = 88) = compose.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            ExhaleTheme(dark = true) {
                Box(Modifier.width(width.dp)) { ExhaleText("23h 59m", ExType.display.copy(fontSize = size.sp), Ex.colors.ink) }
            }
        }
    }

    private fun heroWidth() = compose.onNodeWithContentDescription("23h 59m").getBoundsInRoot().let { it.right - it.left }

    @Test fun aNumberWiderThanTheScreen_shrinksToFit() {
        fontScale = 2f
        hero(width = 300)
        assertTrue(heroWidth() <= 300.dp)
    }

    @Test fun aNewFontScale_measuresAgain_withoutTheTextChanging() {
        // Small type, which font scale grows in full (Android 14 grows big type less).
        hero(width = 411, size = 20)
        val before = heroWidth()
        fontScale = 1.5f
        compose.waitForIdle()
        assertTrue(heroWidth() > before * 1.2f)
    }

    // A log can make the number longer ("25m" → "1h 30m": pieces that come in with no old piece) or,
    // undone, shorter again; and the first log replaces "—". None of it may throw.
    @Test fun changesThatAddOrDropPieces_render() {
        var text by mutableStateOf("25m")
        compose.setContent {
            ExhaleTheme(dark = true) { ExhaleText(text, ExType.display.copy(fontSize = 88.sp), Ex.colors.ink) }
        }
        for (next in listOf("1h 30m", "25m", "—", "1h 30m", "2d 3h", "59m")) {
            compose.runOnUiThread { text = next }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(next).assertExists()
        }
    }

    // The change's first frame is its start: the old text where it was, nothing of the new one yet.
    // (The clock used to start a frame late, so the first frame drew the new "1h 30m" whole, or no
    // number at all, before the animation began.) Compared by where the ink is and how much of it,
    // since the old text, kept centred, may land half a pixel off when the width changes.
    @Test fun theFirstFrameOfAChange_looksLikeTheFrameBefore() {
        var text by mutableStateOf("25m")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ExhaleTheme(dark = true) {
                Box(Modifier.width(411.dp).background(Ex.colors.bg), contentAlignment = Alignment.Center) {
                    ExhaleText(text, ExType.display.copy(fontSize = 88.sp), Ex.colors.ink)
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val before = ink(frame("before"))
        compose.runOnUiThread { text = "1h 30m"; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        val first = ink(frame("first"))
        assertTrue("ink at ${first.box}, was at ${before.box}", abs(first.box.left - before.box.left) <= 2 && abs(first.box.right - before.box.right) <= 2)
        assertTrue("ink ${first.amount}, was ${before.amount}", abs(first.amount - before.amount) <= before.amount / 20)
        // And it moves on from there to the new text, which is wider.
        compose.mainClock.advanceTimeBy(1_000)
        val end = ink(frame("end"))
        assertTrue("ink at ${end.box}, was at ${before.box}", end.box.width() > before.box.width() + 50)
    }

    // A new time that comes while a change is still moving (as when the minute rolls over during
    // one) waits for it, so nothing jumps; the newest time follows once the change has played out.
    // (It used to cut the change short: the half-faded "1h 0m" jumped to full.) Two heroes, one above
    // the other, both change to "1h 0m"; only the top one then gets "59m" mid-change. Until that
    // change has played out, the two must look exactly alike.
    @Test fun aChangeMidAnimation_waitsForItToFinish_thenShowsTheNewest() {
        var top by mutableStateOf("25m")
        var bottom by mutableStateOf("25m")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ExhaleTheme(dark = true) {
                Column(Modifier.width(411.dp).background(Ex.colors.bg)) {
                    for (text in listOf(top, bottom)) {
                        Box(Modifier.fillMaxWidth().height(HALF_DP.dp), contentAlignment = Alignment.Center) {
                            ExhaleText(text, ExType.display.copy(fontSize = 88.sp), Ex.colors.ink)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnUiThread { top = "1h 0m"; bottom = "1h 0m"; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnUiThread { top = "59m"; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        assertHalvesAlike(frame("next_frame"))
        compose.mainClock.advanceTimeBy(150)
        assertHalvesAlike(frame("150ms_on"))
        // Then the top one moves on to "59m", which is narrower than "1h 0m".
        compose.mainClock.advanceTimeBy(1_000)
        val (shownTop, shownBottom) = halves(frame("end")).let { (t, b) -> ink(t) to ink(b) }
        assertTrue("top ink ${shownTop.box}, bottom ${shownBottom.box}", shownTop.box.width() < shownBottom.box.width() - 50)
    }

    /** A frame's pixels, read out of the bitmap once (one native call, not one per pixel). */
    private class Pixels(val width: Int, val height: Int, val argb: IntArray)

    /** The top and bottom halves of [px] (each [HALF_DP] tall). */
    private fun halves(px: Pixels): Pair<Pixels, Pixels> {
        val h = px.height / 2
        val n = px.width * h
        return Pixels(px.width, h, px.argb.copyOfRange(0, n)) to Pixels(px.width, h, px.argb.copyOfRange(n, 2 * n))
    }

    /** Alike, give or take the blur's rounding (a few levels in its faint halo, which can differ by where on screen it is). */
    private fun assertHalvesAlike(px: Pixels) {
        val (a, b) = halves(px)
        var most = 0
        for (i in a.argb.indices) {
            val p = a.argb[i]
            val q = b.argb[i]
            most = maxOf(most, abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q)))
        }
        assertTrue("the halves differ by up to $most levels", most <= 8)
    }

    private class Ink(val box: Rect, val amount: Int)

    /** Where the light text is on the dark background, and how many pixels of it. */
    private fun ink(px: Pixels): Ink {
        // Rect.union(x, y) can't grow right or bottom from the first point (it is if/else-if), so the
        // bounds are kept by hand.
        val box = Rect(px.width, px.height, -1, -1)
        var amount = 0
        for (i in px.argb.indices) {
            if (Color.red(px.argb[i]) > 128) {
                amount++
                val x = i % px.width
                val y = i / px.width
                box.set(minOf(box.left, x), minOf(box.top, y), maxOf(box.right, x), maxOf(box.bottom, y))
            }
        }
        return Ink(box, amount)
    }

    /** The screen as drawn now, through Roborazzi (works with the clock paused, mid-animation). */
    private fun frame(name: String): Pixels {
        val file = File("build/outputs/roborazzi/test_exhale_first_frame_$name.png")
        // Record, so the image is written in a plain test run too (not only when recording screenshots).
        compose.onRoot().captureRoboImage(file.path, RoborazziOptions(taskType = RoborazziTaskType.Record))
        val bmp = BitmapFactory.decodeFile(file.path)
        return Pixels(bmp.width, bmp.height, IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) })
    }

    /** Each piece as "old→new", "+new" (comes), "-old" (goes) or "=same", left to right. */
    private fun changes(old: String?, text: String, resized: Boolean = false) = exhalePairs(old, text, resized).map { p ->
        val n = p.to?.let { text.substring(it) }
        val o = p.from?.let { old?.substring(it) }
        when {
            !p.changed -> "=$n"
            n == null -> "-$o"
            o == null -> "+$n"
            else -> "$o→$n"
        }
    }

    @Test fun piecesAreNumbersAndWhatIsBetweenThem() {
        assertEquals(listOf("1", "h ", "42", "m"), exhaleTokens("1h 42m").map { "1h 42m".substring(it) })
        assertEquals(listOf("—"), exhaleTokens("—").map { "—".substring(it) })
    }

    @Test fun aNumberThatChanged_movesWhole_andTheRestHolds() {
        assertEquals(listOf("25→59", "=m"), changes("25m", "59m"))
        assertEquals(listOf("=1", "=h ", "42→41", "=m"), changes("1h 42m", "1h 41m"))
        assertEquals(listOf("1→2", "=h ", "=0", "=m"), changes("1h 0m", "2h 0m"))
    }

    @Test fun aShorterOrLongerText_onlyTheEndsComeAndGo() {
        assertEquals(listOf("-1", "-h ", "0→59", "=m"), changes("1h 0m", "59m"))
        assertEquals(listOf("+1", "+h ", "1→30", "=m"), changes("1m", "1h 30m"))
    }

    // One of the two had to shrink to fit and the other didn't: a piece with the same text is drawn at
    // another size, so it can't hold still; everything breathes out and settles in.
    @Test fun aTextAtAnotherSize_changesEveryPiece() {
        assertEquals(listOf("+1", "+h ", "59→30", "m→m"), changes("59m", "1h 30m", resized = true))
    }

    @Test fun theFirstShow_changesNothing() {
        assertEquals(listOf("=2", "=h ", "=41", "=m"), changes(null, "2h 41m"))
    }
}
