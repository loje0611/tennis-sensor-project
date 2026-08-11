package io.github.loje0611.tennisdoc.feature.history

import androidx.lifecycle.SavedStateHandle
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.model.CoachingCommentGenerator
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeSwingHistoryRepository
    
    private val fakeCoachingGenerator = object : CoachingCommentGenerator {
        override fun generateComment(
            categoryKey: String,
            metrics: SwingMetrics,
            globalAverage: SwingMetrics?
        ): String = "Fake comment"
    }

    @Before
    fun setup() {
        repository = FakeSwingHistoryRepository()
    }

    @Test
    fun `when sessionId is blank, state is notFound`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to ""))
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator)
        
        val state = viewModel.uiState.first { !it.loading }
        
        assertFalse(state.loading)
        assertTrue(state.notFound)
        assertEquals(null, state.session)
    }

    @Test
    fun `when session exists, state loads session detail`() = runTest {
        val testSessionId = UUID.randomUUID().toString()
        val testSession = SwingSessionEntity(
            sessionId = testSessionId,
            sessionName = "Test Session",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 1000,
            totalSwingCount = 10,
            durationMillis = 1000,
            forehandVolleyCount = 0,
            backhandVolleyCount = 0
        )
        repository.insertProvisionalSession(testSession)
        
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator)
        
        val state = viewModel.uiState.first { !it.loading }
        
        assertFalse(state.loading)
        assertFalse(state.notFound)
        assertEquals(testSession.sessionId, state.session?.sessionId)
    }

    @Test
    fun `deleteSession calls repository delete and triggers onComplete`() = runTest {
        val testSessionId = UUID.randomUUID().toString()
        val testSession = SwingSessionEntity(
            sessionId = testSessionId,
            sessionName = "Test",
            startTime = 0,
            endTime = 0,
            totalSwingCount = 0,
            durationMillis = 0,
            forehandVolleyCount = 0,
            backhandVolleyCount = 0
        )
        repository.insertProvisionalSession(testSession)
        
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator)
        
        // wait for load to finish first
        viewModel.uiState.first { !it.loading }
        
        val channel = kotlinx.coroutines.channels.Channel<Unit>(1)
        viewModel.deleteSession {
            channel.trySend(Unit)
        }
        
        // Wait for callback to be invoked
        channel.receive()
        
        val sessions = repository.observeSessions().first()
        assertTrue(sessions.isEmpty())
    }
}
