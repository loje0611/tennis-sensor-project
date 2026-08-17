package io.github.loje0611.tennisdoc.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import io.github.loje0611.tennisdoc.feature.history.SessionDetailScreen
import io.github.loje0611.tennisdoc.feature.history.SessionDetailViewModel
import io.github.loje0611.tennisdoc.feature.lab.replay.LabReplayScreen
import io.github.loje0611.tennisdoc.feature.lab.replay.LabReplayViewModel
import io.github.loje0611.tennisdoc.session.RecordingSwingHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LabReplayNavigationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ac3AndAc4_swingCardOpensLabReplayRouteAndBackReturnsToSessionDetail() {
        val repository = RecordingSwingHistoryRepository()
        runBlocking {
            repository.insertProvisionalSession(
                SwingSessionEntity(
                    sessionId = "sess-lab-nav",
                    sessionName = "Lab Nav Session",
                    startTime = 1_000L,
                    sessionType = "LAB",
                    drillType = "FOREHAND",
                    totalSwingCount = 1,
                    durationMillis = 60_000L,
                )
            )
            repository.insertLabRawRecord(
                LabRawRecordEntity(
                    id = 777L,
                    sessionId = "sess-lab-nav",
                    drillType = "FOREHAND",
                    timestampMillis = 2_000L,
                    imuRawJson = SAMPLE_IMU_JSON,
                    visionPosesJson = SAMPLE_POSES_JSON,
                )
            )
        }

        val fakeAiCoachPreferences = object : io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository {
            override val geminiApiKey = kotlinx.coroutines.flow.MutableStateFlow<String?>("fake")
            override val llmProvider = kotlinx.coroutines.flow.MutableStateFlow(io.github.loje0611.tennisdoc.core.model.LlmProvider.GEMINI)
            override val defaultCoachTone = kotlinx.coroutines.flow.MutableStateFlow(io.github.loje0611.tennisdoc.core.model.CoachTone.ENCOURAGING)
            override suspend fun setGeminiApiKey(apiKey: String?) {}
            override suspend fun setLlmProvider(provider: io.github.loje0611.tennisdoc.core.model.LlmProvider) {}
            override suspend fun setDefaultCoachTone(tone: io.github.loje0611.tennisdoc.core.model.CoachTone) {}
        }

        val detailViewModel = SessionDetailViewModel(
            SavedStateHandle(mapOf("sessionId" to "sess-lab-nav")),
            repository,
            object : CoachingCommentGenerator {
                override fun generateComment(
                    categoryKey: String,
                    metrics: SwingMetrics,
                    globalAverage: SwingMetrics?,
                ): String = "Good"
            },
            io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser(),
            io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService(),
            fakeAiCoachPreferences,
        )

        composeRule.setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.sessionDetail("sess-lab-nav"),
                ) {
                    composable(
                        route = AppRoutes.SESSION_DETAIL,
                        arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                    ) {
                        SessionDetailScreen(
                            onBack = { navController.popBackStack() },
                            viewModel = detailViewModel,
                            onNavigateToReplay = { sessionId, recordId ->
                                navController.navigate(AppRoutes.createLabReplayRoute(sessionId, recordId))
                            },
                        )
                    }
                    composable(
                        route = AppRoutes.LAB_REPLAY,
                        arguments = listOf(
                            navArgument("sessionId") { type = NavType.StringType },
                            navArgument("recordId") { type = NavType.LongType },
                        ),
                    ) { entry ->
                        val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                        val recordId = entry.arguments?.getLong("recordId") ?: 0L
                        val replayViewModel = remember(recordId) {
                            LabReplayViewModel(
                                SavedStateHandle(
                                    mapOf(
                                        "sessionId" to sessionId,
                                        "recordId" to recordId,
                                    )
                                ),
                                repository,
                            )
                        }
                        LabReplayScreen(
                            viewModel = replayViewModel,
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithText("스윙 #1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("스윙 #1").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithText("동기 리플레이 & 정밀 진단")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("동기 리플레이 & 정밀 진단").assertIsDisplayed()

        composeRule.onNodeWithText("⟵").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithText("스윙 #1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("스윙 #1").assertIsDisplayed()
        composeRule.onNodeWithText("포핸드 훈련").assertIsDisplayed()
    }

    companion object {
        private const val SAMPLE_IMU_JSON =
            "[{\"ts\":2000,\"ax\":0.1,\"ay\":0.2,\"az\":0.3,\"gx\":1.0,\"gy\":2.0,\"gz\":3.0}]"
        private const val SAMPLE_POSES_JSON =
            "[{\"landmarks\":[{\"x\":0.1,\"y\":0.2,\"z\":0.3,\"v\":0.9}]}]"
    }
}
