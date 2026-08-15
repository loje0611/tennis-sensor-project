package io.github.loje0611.tennisdoc.feature.history

import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeSwingHistoryRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setup() {
        repository = FakeSwingHistoryRepository()
        viewModel = HistoryViewModel(repository)
    }

    @Test
    fun `sessions state is initially empty`() = runTest {
        val sessions = viewModel.sessions.first()
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `sessions state observes repository sessions`() = runTest {
        val testSession = SwingSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            sessionName = "Test Session",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 1000,
            totalSwingCount = 10,
            durationMillis = 1000,
            forehandVolleyCount = 0,
            backhandVolleyCount = 0
        )
        
        repository.insertProvisionalSession(testSession)
        
        val sessions = viewModel.sessions.first()
        assertEquals(1, sessions.size)
        assertEquals(testSession.sessionId, sessions[0].sessionId)
    }

    @Test
    fun `insertMockSessionData updates state and repository`() = runTest {
        assertEquals(false, viewModel.mockInsertInProgress.value)
        
        viewModel.insertMockSessionData()
        
        // Wait until a session is inserted
        val sessions = viewModel.sessions.first { it.isNotEmpty() }
        
        // Wait until the insertion process flag is cleared
        viewModel.mockInsertInProgress.first { !it }
        
        assertEquals(1, sessions.size)
        val createdSession = sessions[0]
        assertEquals("LAB", createdSession.sessionType)
        assertEquals("FOREHAND", createdSession.drillType)
        assertEquals(10, createdSession.totalSwingCount)

        val rawRecords = repository.getLabRawRecordsForSession(createdSession.sessionId).first()
        assertEquals(10, rawRecords.size)
        assertTrue(rawRecords.all { it.drillType == "FOREHAND" && it.imuRawJson.isNotBlank() && it.visionPosesJson.isNotBlank() })
    }
}
