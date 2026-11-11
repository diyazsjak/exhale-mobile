package app.exhale.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.ui.components.ExhaleText
import app.exhale.ui.components.exhalePairs
import app.exhale.ui.components.exhaleTokens
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import app.exhale.ui.theme.ExhaleTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hero number fits the screen and follows the font size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ExhaleTextTest {
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

    private class Ink(val box: Rect, val amount: Int)

    /** Where the light text is on the dark background, and how many pixels of it. */
    private fun ink(bmp: Bitmap): Ink {
        val box = Rect(bmp.width, bmp.height, 0, 0)
        var amount = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            if (Color.red(bmp.getPixel(x, y)) > 128) {
                amount++
                box.union(x, y)
            }
        }
        return Ink(box, amount)
    }

    /** The screen as drawn now, through Roborazzi (works with the clock paused, mid-animation). */
    private fun frame(name: String): Bitmap {
        val file = File("build/outputs/roborazzi/test_exhale_first_frame_$name.png")
        // Record, so the image is written in a plain test run too (not only when recording screenshots).
        compose.onRoot().captureRoboImage(file.path, RoborazziOptions(taskType = RoborazziTaskType.Record))
        return BitmapFactory.decodeFile(file.path)
    }

    /** Each piece as "old→new", "+new" (comes), "-old" (goes) or "=same", left to right. */
    private fun changes(old: String?, text: String) = exhalePairs(old, text).map { p ->
        val n = p.to?.let { text.substring(it) }
        val o = p.from?.let { old!!.substring(it) }
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

    @Test fun theFirstShow_changesNothing() {
        assertEquals(listOf("=2", "=h ", "=41", "=m"), changes(null, "2h 41m"))
    }
}
