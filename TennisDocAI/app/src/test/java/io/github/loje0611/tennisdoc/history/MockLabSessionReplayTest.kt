package io.github.loje0611.tennisdoc.history

import androidx.lifecycle.SavedStateHandle
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import io.github.loje0611.tennisdoc.feature.history.MockDataGenerator
import io.github.loje0611.tennisdoc.feature.history.SessionDetailViewModel
import io.github.loje0611.tennisdoc.feature.lab.replay.LabReplayViewModel
import io.github.loje0611.tennisdoc.navigation.AppRoutes
import io.github.loje0611.tennisdoc.session.RecordingSwingHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MockLabSessionReplayTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun ac7_mockLabSessionLoadsDetailSwingsAndReplayPoseImu() = runBlocking {
        val repository = RecordingSwingHistoryRepository()
        MockDataGenerator.generateAndInsert(repository)

        val session = repository.observeSessions().first().single()
        assertEquals("LAB", session.sessionType)
        assertEquals("FOREHAND", session.drillType)
        assertEquals(10, session.totalSwingCount)

        val records = repository.getLabRawRecordsForSession(session.sessionId).first()
        assertEquals(10, records.size)
        val firstRecord = records.first()
        assertTrue(firstRecord.id > 0L)

        val detailViewModel = SessionDetailViewModel(
            SavedStateHandle(mapOf("sessionId" to session.sessionId)),
            repository,
            object : CoachingCommentGenerator {
                override fun generateComment(
                    type: String,
                    current: SwingMetrics,
                    history: SwingMetrics?,
                ): String = "Good"
            },
        )
        val detailState = withTimeout(20_000) {
            detailViewModel.uiState.first { !it.loading && it.labDetailState.swingItems.size == 10 }
        }
        assertEquals(10, detailState.labDetailState.swingItems.size)
        assertEquals(firstRecord.id, detailState.labDetailState.swingItems.first().recordId)
        assertEquals(
            "lab_replay/${session.sessionId}/${firstRecord.id}",
            AppRoutes.createLabReplayRoute(session.sessionId, firstRecord.id),
        )

        val replayViewModel = LabReplayViewModel(
            SavedStateHandle(
                mapOf(
                    "sessionId" to session.sessionId,
                    "recordId" to firstRecord.id,
                )
            ),
            repository,
        )
        val replayState = withTimeout(20_000) {
            replayViewModel.uiState.first { it.fusedSwing != null }
        }
        val fused = replayState.fusedSwing
        assertNotNull(fused)
        assertTrue(fused!!.visionPoses.isNotEmpty())
        assertTrue(fused.imuSamples.isNotEmpty())
        assertEquals(30, fused.visionPoses.size)
        assertEquals(50, fused.imuSamples.size)
    }
}
