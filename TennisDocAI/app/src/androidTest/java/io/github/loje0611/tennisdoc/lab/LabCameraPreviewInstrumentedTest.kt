package io.github.loje0611.tennisdoc.lab

import androidx.camera.view.PreviewView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loje0611.tennisdoc.MainActivity
import io.github.loje0611.tennisdoc.testutil.DeviceTestUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class LabCameraPreviewInstrumentedTest {

    @get:Rule(order = 0)
    val grantCameraRule = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                DeviceTestUtils.grantCameraPermission()
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun labScreen_withCameraGranted_streamsPreviewAndShowsFpsChip() {
        composeRule.waitForIdle()
        DeviceTestUtils.waitForPreviewStreaming(timeoutMs = 25_000L)
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodes(hasText("FPS", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("FPS", substring = true).assertIsDisplayed()
        org.junit.Assert.assertEquals(
            PreviewView.ScaleType.FILL_CENTER,
            DeviceTestUtils.previewViewScaleType(),
        )
    }
}
