package io.github.loje0611.tennisdoc.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import io.github.loje0611.tennisdoc.core.ui.formatDurationMillis
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
@Config(sdk = [28])
class SessionDetailNavigationUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeAiCoachPreferences = object : io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository {
        override val geminiApiKey = kotlinx.coroutines.flow.MutableStateFlow<String?>("fake")
        override val llmProvider = kotlinx.coroutines.flow.MutableStateFlow(io.github.loje0611.tennisdoc.core.model.LlmProvider.GEMINI)
        override val defaultCoachTone = kotlinx.coroutines.flow.MutableStateFlow(io.github.loje0611.tennisdoc.core.model.CoachTone.ENCOURAGING)
        override suspend fun setGeminiApiKey(apiKey: String?) {}
        override suspend fun setLlmProvider(provider: io.github.loje0611.tennisdoc.core.model.LlmProvider) {}
        override suspend fun setDefaultCoachTone(tone: io.github.loje0611.tennisdoc.core.model.CoachTone) {}
    }

    @Test
    fun ac2AndAc3_labSessionRendersSummarySwingCardsAndTriggersReplayCallback() {
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
            repository.insertLabRawRecord(sampleRecord(555L, "sess-lab-999", 2000L))
            repository.insertLabRawRecord(sampleRecord(556L, "sess-lab-999", 3000L))
        }

        val viewModel = SessionDetailViewModel(
            SavedStateHandle(mapOf("sessionId" to "sess-lab-999")),
            repository,
            fakeCoachingGenerator(),
            io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser(),
            io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService(),
            fakeAiCoachPreferences
        )

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

        composeTestRule.waitUntil(timeoutMillis = 15_000L) {
            composeTestRule.onAllNodesWithText("스윙 #2").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("포핸드 훈련").assertIsDisplayed()
        composeTestRule.onNodeWithText("5회 스윙 · ${formatDurationMillis(120000L)}").assertIsDisplayed()
        composeTestRule.onNodeWithText("정타율 (SQUARE)").assertIsDisplayed()
        composeTestRule.onNodeWithText("평균 체인 효율").assertIsDisplayed()
        composeTestRule.onNodeWithText("스윙 #1").assertIsDisplayed()
        composeTestRule.onNodeWithText("스윙 #2").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("스윙 #1").performClick()

        assertEquals("sess-lab-999", clickedSessionId)
        assertEquals(555L, clickedRecordId)
        assertEquals(
            "lab_replay/sess-lab-999/555",
            AppRoutes.createLabReplayRoute(clickedSessionId, clickedRecordId),
        )
    }

    @Test
    fun labSessionWithNoRecords_showsEmptyState() {
        val repository = RecordingSwingHistoryRepository()
        runBlocking {
            repository.insertProvisionalSession(
                SwingSessionEntity(
                    sessionId = "sess-lab-empty",
                    sessionName = "Empty Lab",
                    startTime = 1000L,
                    sessionType = "LAB",
                    drillType = "SERVE",
                    totalSwingCount = 0,
                    durationMillis = 0L,
                )
            )
        }

        val viewModel = SessionDetailViewModel(
            SavedStateHandle(mapOf("sessionId" to "sess-lab-empty")),
            repository,
            fakeCoachingGenerator(),
            io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser(),
            io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService(),
            fakeAiCoachPreferences
        )

        composeTestRule.setContent {
            MaterialTheme {
                SessionDetailScreen(
                    onBack = {},
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000L) {
            composeTestRule.onAllNodesWithText("기록된 스윙 데이터가 없습니다.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("서브 훈련").assertIsDisplayed()
        composeTestRule.onNodeWithText("기록된 스윙 데이터가 없습니다.").performScrollTo().assertIsDisplayed()
    }

    private fun fakeCoachingGenerator(): CoachingCommentGenerator =
        object : CoachingCommentGenerator {
            override fun generateComment(
                categoryKey: String,
                metrics: SwingMetrics,
                globalAverage: SwingMetrics?,
            ): String = "Good"
        }

    private fun sampleRecord(id: Long, sessionId: String, timestampMillis: Long) =
        LabRawRecordEntity(
            id = id,
            sessionId = sessionId,
            drillType = "FOREHAND",
            timestampMillis = timestampMillis,
            imuRawJson = "[{\"ts\":2000,\"ax\":0.1,\"ay\":0.2,\"az\":0.3,\"gx\":1.0,\"gy\":2.0,\"gz\":3.0}]",
            visionPosesJson = "[{\"landmarks\":[{\"x\":0.1,\"y\":0.2,\"z\":0.3,\"v\":0.9}]}]",
        )
}
