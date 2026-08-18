package io.github.loje0611.tennisdoc.lab

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.loje0611.tennisdoc.feature.lab.replay.SwingTrailOverlay
import io.github.loje0611.tennisdoc.feature.lab.replay.TrailPoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SwingTrailOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val trail = listOf(
        TrailPoint(x = 0.2f, y = 0.3f, progress = 0f),
        TrailPoint(x = 0.5f, y = 0.5f, progress = 0.5f),
        TrailPoint(x = 0.55f, y = 0.55f, progress = 1f),
    )

    @Test
    fun impactBadgeShownWhenIsImpactAndTrailPointsPresent() {
        composeRule.setContent {
            MaterialTheme {
                SwingTrailOverlay(
                    swingTrailPoints = trail,
                    isImpact = true,
                    canvasSize = Size(200f, 200f),
                    modifier = Modifier.size(200.dp, 200.dp),
                )
            }
        }

        composeRule.onNodeWithText("IMPACT!").assertIsDisplayed()
    }

    @Test
    fun impactBadgeHiddenWhenNotImpact() {
        composeRule.setContent {
            MaterialTheme {
                SwingTrailOverlay(
                    swingTrailPoints = trail,
                    isImpact = false,
                    canvasSize = Size(200f, 200f),
                    modifier = Modifier.size(200.dp, 200.dp),
                )
            }
        }

        assertEquals(0, composeRule.onAllNodesWithText("IMPACT!").fetchSemanticsNodes().size)
    }

    @Test
    fun emptyTrailRendersNoImpactBadgeEvenWhenIsImpact() {
        composeRule.setContent {
            MaterialTheme {
                SwingTrailOverlay(
                    swingTrailPoints = emptyList(),
                    isImpact = true,
                    canvasSize = Size(200f, 200f),
                    modifier = Modifier.size(200.dp, 200.dp),
                )
            }
        }

        assertEquals(0, composeRule.onAllNodesWithText("IMPACT!").fetchSemanticsNodes().size)
    }
}
