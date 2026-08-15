package io.github.loje0611.tennisdoc.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import io.github.loje0611.tennisdoc.feature.history.SessionDetailScreen
import io.github.loje0611.tennisdoc.feature.history.SessionDetailViewModel
import io.github.loje0611.tennisdoc.session.RecordingSwingHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SessionDetailNavigationUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sessionDetailScreen_rendersLabSessionSummaryAndTriggersReplay() {
        val repository = RecordingSwingHistoryRepository()
        val testSession = SwingSessionEntity(
            sessionId = "sess-lab-999",
            sessionName = "2026.08.15 03:40 PM",
            startTime = 1000L,
            sessionType = "LAB",
            drillType = "FOREHAND",
            totalSwingCount = 5,
            durationMillis = 120000L,
        )
        runBlocking {
            repository.insertProvisionalSession(testSession)
            repository.insertLabRawRecord(
                LabRawRecordEntity(
                    id = 555L,
                    sessionId = "sess-lab-999",
                    drillType = "FOREHAND",
                    timestampMillis = 2000L,
                    imuRawJson = "[{\"ts\":2000,\"ax\":0.1,\"ay\":0.2,\"az\":0.3,\"gx\":1.0,\"gy\":2.0,\"gz\":3.0}]",
                    visionPosesJson = "[{\"landmarks\":[{\"x\":0.1,\"y\":0.2,\"z\":0.3,\"v\":0.9}]}]",
                )
            )
        }

        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to "sess-lab-999"))
        val coachingGenerator = object : CoachingCommentGenerator {
            override fun generateComment(
                type: String,
                current: SwingMetrics,
                history: SwingMetrics?
            ): String = "Good"
        }

        val viewModel = SessionDetailViewModel(savedStateHandle, repository, coachingGenerator)

        var clickedSessionId = ""
        var clickedRecordId = 0L

        composeTestRule.setContent {
            MaterialTheme {
                SessionDetailScreen(
                    onBack = {},
                    viewModel = viewModel,
                    onNavigateToReplay = { sId, rId ->
                        clickedSessionId = sId
                        clickedRecordId = rId
                    },
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify summary card
        composeTestRule.onNodeWithText("포핸드 훈련").assertExists()
        composeTestRule.onNodeWithText("정타율 (SQUARE)").assertExists()
        composeTestRule.onNodeWithText("평균 체인 효율").assertExists()

        // Verify swing item card
        composeTestRule.onNodeWithText("스윙 #1").assertExists()
        composeTestRule.onNodeWithText("스윙 #1").performClick()

        assertEquals("sess-lab-999", clickedSessionId)
        assertEquals(555L, clickedRecordId)
    }
}
