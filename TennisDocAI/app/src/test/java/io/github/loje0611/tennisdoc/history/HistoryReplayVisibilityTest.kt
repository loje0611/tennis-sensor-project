package io.github.loje0611.tennisdoc.history

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
import io.github.loje0611.tennisdoc.feature.history.SessionDetailScreen
import io.github.loje0611.tennisdoc.feature.history.SessionDetailViewModel
import io.github.loje0611.tennisdoc.session.RecordingSwingHistoryRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoryReplayVisibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fakeAiCoachPreferences = object : io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository {
        override val geminiApiKey = kotlinx.coroutines.flow.MutableStateFlow<String?>("fake")
        override val llmProvider = kotlinx.coroutines.flow.MutableStateFlow(io.github.loje0611.tennisdoc.core.model.LlmProvider.GEMINI)
        override val defaultCoachTone = kotlinx.coroutines.flow.MutableStateFlow(io.github.loje0611.tennisdoc.core.model.CoachTone.ENCOURAGING)
        override suspend fun setGeminiApiKey(apiKey: String?) {}
        override suspend fun setLlmProvider(provider: io.github.loje0611.tennisdoc.core.model.LlmProvider) {}
        override suspend fun setDefaultCoachTone(tone: io.github.loje0611.tennisdoc.core.model.CoachTone) {}
    }

    @Test
    fun videoBadgeShownOnlyForExistingFileAndOnlyThatCardNavigates() {
        val video = File.createTempFile("history-swing", ".mp4")
        try {
            video.writeBytes(byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70))
            val repository = RecordingSwingHistoryRepository()
            runBlocking {
                repository.insertProvisionalSession(
                    SwingSessionEntity(
                        sessionId = "sess-hist-video",
                        sessionName = "History Video Session",
                        startTime = 1_000L,
                        sessionType = "LAB",
                        drillType = "FOREHAND",
                        totalSwingCount = 2,
                        durationMillis = 60_000L,
                    )
                )
                repository.insertLabRawRecord(
                    sampleRecord(
                        id = 101L,
                        sessionId = "sess-hist-video",
                        timestampMillis = 2_000L,
                        videoPath = video.absolutePath,
                    )
                )
                repository.insertLabRawRecord(
                    sampleRecord(
                        id = 102L,
                        sessionId = "sess-hist-video",
                        timestampMillis = 3_000L,
                        videoPath = null,
                    )
                )
            }

            val viewModel = SessionDetailViewModel(
                SavedStateHandle(mapOf("sessionId" to "sess-hist-video")),
                repository,
                fakeCoachingGenerator(),
                io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser(),
                io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService(),
                fakeAiCoachPreferences,
            )

            var clickedSessionId = ""
            var clickedRecordId = 0L
            var navigateCount = 0

            composeRule.setContent {
                MaterialTheme {
                    SessionDetailScreen(
                        onBack = {},
                        viewModel = viewModel,
                        onNavigateToReplay = { sessionId, recordId ->
                            clickedSessionId = sessionId
                            clickedRecordId = recordId
                            navigateCount++
                        },
                    )
                }
            }

            composeRule.waitUntil(timeoutMillis = 15_000L) {
                composeRule.onAllNodesWithText("스윙 #2").fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithText("스윙 #1").assertIsDisplayed()
            composeRule.onNodeWithText("스윙 #2").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("🎬 영상 보기").assertIsDisplayed()
            assertEquals(1, composeRule.onAllNodesWithText("🎬 영상 보기").fetchSemanticsNodes().size)

            composeRule.onNodeWithText("스윙 #2").performScrollTo().performClick()
            composeRule.waitForIdle()
            assertEquals(0, navigateCount)

            composeRule.onNodeWithText("스윙 #1").performScrollTo().performClick()
            composeRule.waitForIdle()
            assertEquals(1, navigateCount)
            assertEquals("sess-hist-video", clickedSessionId)
            assertEquals(101L, clickedRecordId)
        } finally {
            video.delete()
        }
    }

    @Test
    fun missingFileAtRecordedPath_hidesVideoBadgeAndDoesNotNavigate() {
        val missing = File(System.getProperty("java.io.tmpdir"), "gone-swing-${System.nanoTime()}.mp4")
        val repository = RecordingSwingHistoryRepository()
        runBlocking {
            repository.insertProvisionalSession(
                SwingSessionEntity(
                    sessionId = "sess-hist-gone",
                    sessionName = "Gone Video Session",
                    startTime = 1_000L,
                    sessionType = "LAB",
                    drillType = "FOREHAND",
                    totalSwingCount = 1,
                    durationMillis = 30_000L,
                )
            )
            repository.insertLabRawRecord(
                sampleRecord(
                    id = 201L,
                    sessionId = "sess-hist-gone",
                    timestampMillis = 2_000L,
                    videoPath = missing.absolutePath,
                )
            )
        }

        val viewModel = SessionDetailViewModel(
            SavedStateHandle(mapOf("sessionId" to "sess-hist-gone")),
            repository,
            fakeCoachingGenerator(),
            io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser(),
            io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService(),
            fakeAiCoachPreferences,
        )

        var navigateCount = 0
        composeRule.setContent {
            MaterialTheme {
                SessionDetailScreen(
                    onBack = {},
                    viewModel = viewModel,
                    onNavigateToReplay = { _, _ -> navigateCount++ },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithText("스윙 #1").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, composeRule.onAllNodesWithText("🎬 영상 보기").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("스윙 #1").performClick()
        composeRule.waitForIdle()
        assertEquals(0, navigateCount)
    }

    private fun fakeCoachingGenerator(): CoachingCommentGenerator =
        object : CoachingCommentGenerator {
            override fun generateComment(
                categoryKey: String,
                metrics: SwingMetrics,
                globalAverage: SwingMetrics?,
            ): String = "Good"
        }

    private fun sampleRecord(
        id: Long,
        sessionId: String,
        timestampMillis: Long,
        videoPath: String?,
    ) = LabRawRecordEntity(
        id = id,
        sessionId = sessionId,
        drillType = "FOREHAND",
        timestampMillis = timestampMillis,
        imuRawJson = "[{\"ts\":2000,\"ax\":0.1,\"ay\":0.2,\"az\":0.3,\"gx\":1.0,\"gy\":2.0,\"gz\":3.0}]",
        visionPosesJson = "[{\"landmarks\":[{\"x\":0.1,\"y\":0.2,\"z\":0.3,\"v\":0.9}]}]",
        videoPath = videoPath,
    )
}
