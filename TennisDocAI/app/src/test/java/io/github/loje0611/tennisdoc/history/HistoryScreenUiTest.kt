package io.github.loje0611.tennisdoc.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.ui.formatDurationMillis
import io.github.loje0611.tennisdoc.feature.history.HistoryScreen
import io.github.loje0611.tennisdoc.feature.history.HistoryViewModel
import io.github.loje0611.tennisdoc.session.RecordingSwingHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoryScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ac1_labHistoryCardShowsDrillDatetimeSwingCountAndDuration() {
        val repository = RecordingSwingHistoryRepository()
        val startTime = 1_776_000_000_000L
        val durationMillis = 120_000L
        runBlocking {
            repository.insertProvisionalSession(
                SwingSessionEntity(
                    sessionId = "sess-hist-fh",
                    sessionName = SwingSessionEntity.formatSessionName(startTime),
                    startTime = startTime,
                    sessionType = "LAB",
                    drillType = "FOREHAND",
                    totalSwingCount = 7,
                    durationMillis = durationMillis,
                )
            )
            repository.insertProvisionalSession(
                SwingSessionEntity(
                    sessionId = "sess-hist-fv",
                    sessionName = SwingSessionEntity.formatSessionName(startTime + 3_600_000L),
                    startTime = startTime + 3_600_000L,
                    sessionType = "LAB",
                    drillType = "FOREHAND_VOLLEY",
                    totalSwingCount = 3,
                    durationMillis = 5_000L,
                )
            )
            repository.insertProvisionalSession(
                SwingSessionEntity(
                    sessionId = "sess-hist-lab-fallback",
                    sessionName = SwingSessionEntity.formatSessionName(startTime + 7_200_000L),
                    startTime = startTime + 7_200_000L,
                    sessionType = "LAB",
                    drillType = null,
                    totalSwingCount = 1,
                    durationMillis = 1_000L,
                )
            )
        }

        val viewModel = HistoryViewModel(repository)
        var openedSessionId = ""

        composeRule.setContent {
            MaterialTheme {
                HistoryScreen(
                    onNavigateToSessionDetail = { openedSessionId = it },
                    viewModel = viewModel,
                    debugModeEnabled = false,
                )
            }
        }

        val datetime = SwingSessionEntity.formatSessionName(startTime)
        val duration = formatDurationMillis(durationMillis)

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithText("포핸드 훈련", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithText("포핸드 훈련", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(datetime, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("7회 스윙 · $duration", useUnmergedTree = true).assertIsDisplayed()

        val cardDesc = "$datetime, 포핸드 훈련, 7회 스윙, $duration"
        composeRule.onNodeWithContentDescription(cardDesc).performClick()
        assertEquals("sess-hist-fh", openedSessionId)

        composeRule.onNodeWithText("포발리 훈련", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Lab 훈련", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}
