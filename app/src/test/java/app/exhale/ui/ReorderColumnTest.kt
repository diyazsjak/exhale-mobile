package app.exhale.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.exhale.ui.components.ReorderColumn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One drag can carry a row past several others, and the move is reported once, on release. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReorderColumnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dragPastTwoRows_reportsOneMove() {
        val moves = mutableListOf<Pair<Int, Int>>()
        compose.setContent {
            ReorderColumn(listOf("A", "B", "C", "D"), keyOf = { it }, onMove = { from, to -> moves += from to to }) { item, handle, _ ->
                Box(Modifier.height(50.dp)) {
                    Box(handle.size(50.dp).testTag("handle-$item"))
                    Text(item)
                }
            }
        }
        compose.onNodeWithTag("handle-A").performTouchInput {
            down(center)
            // 2.6 rows down, in small steps like a finger.
            repeat(26) { moveBy(androidx.compose.ui.geometry.Offset(0f, height / 10f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(0 to 2), moves)
    }
}
