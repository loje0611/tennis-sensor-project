package io.github.loje0611.tennisdoc.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class AppNavigationInstrumentedTest {

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
    fun bottomBar_navigatesLabHistorySettings() {
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("History").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithText("History").fetchSemanticsNodes().size >= 2
        }

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithText("Sensor Calibration").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Sensor Calibration").assertIsDisplayed()

        composeRule.onNodeWithText("Lab").performClick()
        DeviceTestUtils.waitForPreviewViewDisplayed(timeoutMs = 20_000L)
    }
}
