package io.github.loje0611.tennisdoc.lab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.loje0611.tennisdoc.MainActivity
import io.github.loje0611.tennisdoc.testutil.DeviceTestUtils
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class LabCameraPermissionInstrumentedTest {

    @get:Rule(order = 0)
    val revokeCameraRule = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                DeviceTestUtils.revokeCameraPermission()
                try {
                    base.evaluate()
                } finally {
                    DeviceTestUtils.grantCameraPermission()
                }
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun labScreen_withoutCamera_showsRationaleAndSystemPermissionDialog() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("카메라 권한이 필요합니다.").assertIsDisplayed()
        composeRule.onNodeWithText("권한 허용").assertIsDisplayed()
        composeRule.onNodeWithText("권한 허용").performClick()

        val dialogHandled = DeviceTestUtils.clickSystemPermissionAllow(timeoutMs = 10_000L)
        assertTrue(
            "OS camera permission dialog did not appear or Allow was not clickable",
            dialogHandled,
        )

        DeviceTestUtils.waitForPreviewStreaming(timeoutMs = 25_000L)
    }
}
