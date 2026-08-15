package io.github.loje0611.tennisdoc.lab

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import io.github.loje0611.tennisdoc.feature.lab.ui.PoseOverlayCanvas
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PoseOverlayCanvasTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dualStrokeSkeleton_rendersWithoutCrashOnEmptyPose() {
        composeTestRule.setContent {
            MaterialTheme {
                PoseOverlayCanvas(
                    poseFrame = null,
                    isMirrored = false
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun dualStrokeSkeleton_rendersWithoutCrashOnFullPose() {
        val fullPose = PoseFrame(
            landmarks = (0 until 33).map {
                PoseLandmark(
                    x = 0.5f,
                    y = 0.5f,
                    z = 0f,
                    visibility = 0.95f
                )
            }
        )

        composeTestRule.setContent {
            MaterialTheme {
                PoseOverlayCanvas(
                    poseFrame = fullPose,
                    isMirrored = true
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
