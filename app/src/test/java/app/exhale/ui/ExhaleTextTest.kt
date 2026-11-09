package app.exhale.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.ui.components.ExhaleText
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
}
