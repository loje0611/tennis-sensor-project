package io.github.loje0611.tennisdoc.settings

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

/**
 * TASK-030 FAIL-2: Settings 캘리브레이션은 BLE 권한 없이 스캔을 시작하지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class SettingsBlePermissionInstrumentedTest {

    @get:Rule(order = 0)
    val permissionRule = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                DeviceTestUtils.grantCameraPermission()
                DeviceTestUtils.revokeBleRuntimePermissions()
                try {
                    base.evaluate()
                } finally {
                    DeviceTestUtils.grantBleRuntimePermissions()
                }
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun calibrationDialog_withoutBlePermission_asksForPermissionInsteadOfScanning() {
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithText("Sensor Calibration").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Sensor Calibration").performClick()

        composeRule.waitUntil(timeoutMillis = 8_000L) {
            composeRule.onAllNodesWithText("센서 영점 조절").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("블루투스 및 위치 권한이 필요합니다.").assertIsDisplayed()
        composeRule.onNodeWithText("권한 허용").assertIsDisplayed()
        org.junit.Assert.assertTrue(
            composeRule.onAllNodesWithText("시작").fetchSemanticsNodes().isEmpty(),
        )
    }
}
