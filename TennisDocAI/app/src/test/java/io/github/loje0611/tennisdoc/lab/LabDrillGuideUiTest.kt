package io.github.loje0611.tennisdoc.lab

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.feature.lab.ui.DrillSelectorBar
import io.github.loje0611.tennisdoc.feature.lab.ui.LabSessionControlHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabDrillGuideUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ac1AndAc2_drillSelectorUpdatesSelectionAndDisablesDuringSession() {
        var selected by mutableStateOf(DrillType.FOREHAND_TOPSPIN)
        var sessionActive by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                DrillSelectorBar(
                    selectedDrill = selected,
                    isSessionActive = sessionActive,
                    onSelectDrill = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("포핸드 플랫").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(DrillType.FOREHAND_FLAT, selected)

        sessionActive = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("포핸드 플랫").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("백핸드 탑스핀").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(DrillType.FOREHAND_FLAT, selected)
    }

    @Test
    fun ac3_startButtonCallsStartAndTogglesToFinishLabel() {
        var sessionActive by mutableStateOf(false)
        var startClicked = false
        var finishClicked = false

        composeRule.setContent {
            MaterialTheme {
                LabSessionControlHeader(
                    selectedDrill = DrillType.FOREHAND_TOPSPIN,
                    isSessionActive = sessionActive,
                    sessionDurationSeconds = 65L,
                    swingCount = 3,
                    isSensorConnected = true,
                    onStartSession = {
                        startClicked = true
                        sessionActive = true
                    },
                    onFinishSession = {
                        finishClicked = true
                        sessionActive = false
                    }
                )
            }
        }

        composeRule.onNodeWithText("측정 시작").assertIsDisplayed()
        composeRule.onNodeWithText("센서 연결됨").assertIsDisplayed()
        composeRule.onNodeWithText("목표: 포핸드 탑스핀").assertIsDisplayed()

        composeRule.onNodeWithText("측정 시작").performClick()
        composeRule.waitForIdle()

        assertTrue(startClicked)
        composeRule.onNodeWithText("측정 종료").assertIsDisplayed()
        composeRule.onNodeWithText("01:05 | 스윙 3회").assertIsDisplayed()

        composeRule.onNodeWithText("측정 종료").performClick()
        composeRule.waitForIdle()
        assertTrue(finishClicked)
        composeRule.onNodeWithText("측정 시작").assertIsDisplayed()
    }
}
