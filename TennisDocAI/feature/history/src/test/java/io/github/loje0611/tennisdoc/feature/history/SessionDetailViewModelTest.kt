package io.github.loje0611.tennisdoc.feature.history

import androidx.lifecycle.SavedStateHandle
import io.github.loje0611.tennisdoc.core.coach.parser.StructuredReportParser
import io.github.loje0611.tennisdoc.core.coach.service.CompositeAiCoachService
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
    
    private val reportParser = StructuredReportParser()
    private val compositeAiCoachService = CompositeAiCoachService()
    

    private val fakeAiCoachPreferences = object : io.github.loje0611.tennisdoc.core.data.repository.AiCoachPreferencesRepository {
        override val geminiApiKey = kotlinx.coroutines.flow.MutableStateFlow<String?>("fake_api_key")
        override val llmProvider = kotlinx.coroutines.flow.MutableStateFlow(io.github.loje0611.tennisdoc.core.model.LlmProvider.GEMINI)
        override val defaultCoachTone = kotlinx.coroutines.flow.MutableStateFlow(io.github.loje0611.tennisdoc.core.model.CoachTone.ENCOURAGING)
        override suspend fun setGeminiApiKey(apiKey: String?) { geminiApiKey.value = apiKey }
        override suspend fun setLlmProvider(provider: io.github.loje0611.tennisdoc.core.model.LlmProvider) { llmProvider.value = provider }
        override suspend fun setDefaultCoachTone(tone: io.github.loje0611.tennisdoc.core.model.CoachTone) { defaultCoachTone.value = tone }
    }

    @Before
    fun setup() {
        repository = FakeSwingHistoryRepository()
    }

    @Test
    fun `when sessionId is blank, state is notFound`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to ""))
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator, reportParser, compositeAiCoachService, fakeAiCoachPreferences)
        
        val state = viewModel.uiState.first { !it.loading }
        
        assertFalse(state.loading)
        assertTrue(state.notFound)
        assertEquals(null, state.session)
    }

    @Test
    fun `when sessionId key is missing from SavedStateHandle, state is notFound`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator, reportParser, compositeAiCoachService, fakeAiCoachPreferences)

        val state = viewModel.uiState.first { !it.loading }

        assertFalse(state.loading)
        assertTrue(state.notFound)
        assertEquals(null, state.session)
    }

    @Test
    fun `when sessionId is unknown, state is notFound`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to "missing-session"))
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator, reportParser, compositeAiCoachService, fakeAiCoachPreferences)

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
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator, reportParser, compositeAiCoachService, fakeAiCoachPreferences)
        
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
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator, reportParser, compositeAiCoachService, fakeAiCoachPreferences)
        
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

    @Test
    fun `when session is LAB type, loads lab raw records and maps summary items`() = runTest {
        val testSessionId = UUID.randomUUID().toString()
        val testSession = SwingSessionEntity(
            sessionId = testSessionId,
            sessionName = "Lab Forehand Session",
            startTime = 1000L,
            sessionType = "LAB",
            drillType = "FOREHAND",
            totalSwingCount = 2,
            durationMillis = 60000L,
        )
        repository.insertProvisionalSession(testSession)

        val imuJson1 = "[{\"ts\":1000,\"ax\":0.1,\"ay\":0.2,\"az\":0.3,\"gx\":1.0,\"gy\":2.0,\"gz\":3.0}]"
        val posesJson1 = "[{\"landmarks\":[{\"x\":0.1,\"y\":0.2,\"z\":0.3,\"v\":0.9}]}]"
        val record1 = io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity(
            id = 101L,
            sessionId = testSessionId,
            drillType = "FOREHAND",
            timestampMillis = 1000L,
            imuRawJson = imuJson1,
            visionPosesJson = posesJson1,
        )
        val record2 = io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity(
            id = 102L,
            sessionId = testSessionId,
            drillType = "FOREHAND",
            timestampMillis = 2000L,
            imuRawJson = imuJson1,
            visionPosesJson = posesJson1,
        )
        repository.insertLabRawRecord(record1)
        repository.insertLabRawRecord(record2)

        val savedStateHandle = SavedStateHandle(mapOf("sessionId" to testSessionId))
        val viewModel = SessionDetailViewModel(savedStateHandle, repository, fakeCoachingGenerator, reportParser, compositeAiCoachService, fakeAiCoachPreferences)

        val state = viewModel.uiState.first { !it.loading && it.labDetailState.swingItems.isNotEmpty() }

        assertEquals(2, state.labDetailState.swingItems.size)
        assertEquals(101L, state.labDetailState.swingItems[0].recordId)
        assertEquals(1, state.labDetailState.swingItems[0].swingIndex)
        assertEquals(102L, state.labDetailState.swingItems[1].recordId)
        assertEquals(2, state.labDetailState.swingItems[1].swingIndex)
        assertFalse(state.labDetailState.isLoading)
    }

    @Test
    fun `when LAB session has no raw records, swing list stays empty`() = runTest {
        val testSessionId = UUID.randomUUID().toString()
        repository.insertProvisionalSession(
            SwingSessionEntity(
                sessionId = testSessionId,
                sessionName = "Empty Lab",
                startTime = 1000L,
                sessionType = "LAB",
                drillType = "BACKHAND",
                totalSwingCount = 0,
                durationMillis = 0L,
            )
        )

        val viewModel = SessionDetailViewModel(
            SavedStateHandle(mapOf("sessionId" to testSessionId)),
            repository,
            fakeCoachingGenerator,
            reportParser,
            compositeAiCoachService,
            fakeAiCoachPreferences
        )

        val state = viewModel.uiState.first { !it.loading && !it.labDetailState.isLoading }

        assertFalse(state.notFound)
        assertEquals("LAB", state.session?.sessionType)
        assertTrue(state.labDetailState.swingItems.isEmpty())
        assertEquals(0, state.labDetailState.squareRatePercent)
        assertEquals(0f, state.labDetailState.averageEnergyEfficiency, 0.01f)
    }
}
