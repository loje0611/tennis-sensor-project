package io.github.loje0611.tennisdoc.lab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import io.github.loje0611.tennisdoc.feature.lab.ui.PoseOverlayCanvas
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PoseOverlayCanvasTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun ac3_dualStrokeSkeleton_rendersWithoutCrashOnEmptyPose() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(240.dp)) {
                    PoseOverlayCanvas(
                        poseFrame = null,
                        isMirrored = false,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().assertExists()
    }

    @Test
    fun ac3_dualStrokeSkeleton_rendersSpreadUpperAndLowerJointsWithoutCrash() {
        val landmarks = List(33) { index ->
            val x = when (index) {
                11, 13, 15 -> 0.35f
                12, 14, 16 -> 0.65f
                23, 25, 27 -> 0.40f
                24, 26, 28 -> 0.60f
                else -> 0.50f
            }
            val y = when (index) {
                in 11..12 -> 0.25f
                in 13..14 -> 0.35f
                in 15..16 -> 0.45f
                in 23..24 -> 0.55f
                in 25..26 -> 0.70f
                in 27..28 -> 0.85f
                else -> 0.50f
            }
            PoseLandmark(x = x, y = y, z = 0f, visibility = 0.95f)
        }

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(320.dp, 480.dp)) {
                    PoseOverlayCanvas(
                        poseFrame = PoseFrame(landmarks = landmarks),
                        isMirrored = true,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().assertExists()
    }
}
