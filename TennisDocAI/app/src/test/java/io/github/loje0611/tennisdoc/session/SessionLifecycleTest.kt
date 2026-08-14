package io.github.loje0611.tennisdoc.session

import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionLifecycleTest {

    private lateinit var repo: RecordingSwingHistoryRepository

    @Before
    fun setUp() {
        SwingAnalysisSessionState.resetSessionUiState()
        repo = RecordingSwingHistoryRepository()
        SwingAnalysisSessionState.historyRepository = repo
    }

    @After
    fun tearDown() {
        SwingAnalysisSessionState.resetSessionUiState()
        SwingAnalysisSessionState.historyRepository = null
    }

    @Test
    fun startSessionExposesFlowsAndApis() {
        assertNull(SwingAnalysisSessionState.activeSessionId.value)
        assertNull(SwingAnalysisSessionState.activeSessionType.value)
        assertNull(SwingAnalysisSessionState.activeDrillType.value)
        assertFalse(SwingAnalysisSessionState.isSessionActive.value)

        val sid = SwingAnalysisSessionState.startSession(
            SessionType.LAB,
            DrillType.FOREHAND_TOPSPIN,
        )

        assertEquals(sid, SwingAnalysisSessionState.activeSessionId.value)
        assertEquals(SessionType.LAB, SwingAnalysisSessionState.activeSessionType.value)
        assertEquals(DrillType.FOREHAND_TOPSPIN, SwingAnalysisSessionState.activeDrillType.value)
        assertTrue(SwingAnalysisSessionState.isSessionActive.value)
        assertEquals(0, SwingAnalysisSessionState.swingCount.value)
        assertTrue(sid.isNotBlank())

        SwingAnalysisSessionState.cancelSession()
        assertFalse(SwingAnalysisSessionState.isSessionActive.value)
        assertNull(SwingAnalysisSessionState.activeSessionId.value)
    }

    @Test
    fun bleConnectedDoesNotInsertProvisionalSession() {
        SwingAnalysisSessionState.updateConnection(BleConnectionState.Connected)

        assertTrue(SwingAnalysisSessionState.connectionState.value.isConnected)
        assertFalse(SwingAnalysisSessionState.isSessionActive.value)
        assertNull(SwingAnalysisSessionState.activeSessionId.value)

        Thread.sleep(250)

        assertTrue(repo.provisionalInserts.isEmpty())
        assertTrue(repo.finalizeCalls.isEmpty())
        assertTrue(repo.deletedSessionIds.isEmpty())
        assertTrue(repo.insertedEvents.isEmpty())
    }

    @Test
    fun bleDisconnectWithoutActiveSessionDoesNotTouchRepository() {
        SwingAnalysisSessionState.updateConnection(BleConnectionState.Connected)
        SwingAnalysisSessionState.updateConnection(BleConnectionState.Disconnected)

        Thread.sleep(250)

        assertTrue(repo.provisionalInserts.isEmpty())
        assertTrue(repo.finalizeCalls.isEmpty())
        assertTrue(repo.deletedSessionIds.isEmpty())
    }

    @Test
    fun startSessionLabForehandTopspinInsertsTypedProvisionalRow() {
        val sid = SwingAnalysisSessionState.startSession(
            SessionType.LAB,
            DrillType.FOREHAND_TOPSPIN,
        )

        assertTrue(SwingAnalysisSessionState.isSessionActive.value)
        awaitUntil { repo.provisionalInserts.isNotEmpty() }

        val inserted = repo.provisionalInserts.single()
        assertEquals(sid, inserted.sessionId)
        assertEquals(SessionType.LAB.name, inserted.sessionType)
        assertEquals(DrillType.FOREHAND_TOPSPIN.name, inserted.drillType)
        assertTrue(inserted.startTime > 0L)
        assertNull(inserted.endTime)
        assertEquals(0, inserted.totalSwingCount)
    }

    @Test
    fun inactiveSessionDoesNotPersistSwingEventsWhenLabelUpdates() {
        SwingAnalysisSessionState.updateConnection(BleConnectionState.Connected)
        SwingAnalysisSessionState.updateSwingLabel("Forehand_Topspin")
        SwingAnalysisSessionState.incrementSwingCount("Forehand_Topspin")

        Thread.sleep(250)

        assertFalse(SwingAnalysisSessionState.isSessionActive.value)
        assertNull(SwingAnalysisSessionState.activeSessionId.value)
        assertTrue(repo.insertedEvents.isEmpty())
        assertTrue(repo.provisionalInserts.isEmpty())
    }

    @Test
    fun finishSessionFinalizesCountsAndPreservesSessionAndDrillType() {
        val sid = SwingAnalysisSessionState.startSession(
            SessionType.LAB,
            DrillType.FOREHAND_TOPSPIN,
        )
        awaitUntil { repo.provisionalInserts.any { it.sessionId == sid } }

        SwingAnalysisSessionState.incrementSwingCount("Forehand_Topspin")
        SwingAnalysisSessionState.incrementSwingCount("Forehand_Topspin")
        SwingAnalysisSessionState.updateSessionDuration(5L)

        SwingAnalysisSessionState.finishSession()

        assertFalse(SwingAnalysisSessionState.isSessionActive.value)
        assertNull(SwingAnalysisSessionState.activeSessionId.value)
        assertNull(SwingAnalysisSessionState.activeSessionType.value)
        assertNull(SwingAnalysisSessionState.activeDrillType.value)

        awaitUntil { repo.finalizeCalls.isNotEmpty() }

        val call = repo.finalizeCalls.single()
        assertEquals(sid, call.sessionId)
        assertEquals(2, call.totalSwingCount)
        assertEquals(5_000L, call.durationMillis)
        assertTrue(call.endTime > 0L)
        assertEquals(2, call.breakdownNormalized["forehand topspin"])

        val stored = repo.getSessionDetailBlocking(sid)
        assertNotNull(stored)
        assertEquals(SessionType.LAB.name, stored!!.session.sessionType)
        assertEquals(DrillType.FOREHAND_TOPSPIN.name, stored.session.drillType)
        assertEquals(2, stored.session.totalSwingCount)
        assertEquals(5_000L, stored.session.durationMillis)
        assertEquals(call.endTime, stored.session.endTime)
    }

    @Test
    fun finishSessionWithZeroSwingsDeletesProvisionalRow() {
        val sid = SwingAnalysisSessionState.startSession(SessionType.MATCH)
        awaitUntil { repo.provisionalInserts.any { it.sessionId == sid } }

        SwingAnalysisSessionState.finishSession()

        awaitUntil { repo.deletedSessionIds.contains(sid) }
        assertTrue(repo.finalizeCalls.isEmpty())
        assertNull(repo.getSessionDetailBlocking(sid))
    }

    @Test
    fun cancelSessionDeletesWithoutFinalizing() {
        val sid = SwingAnalysisSessionState.startSession(
            SessionType.LAB,
            DrillType.SERVE,
        )
        awaitUntil { repo.provisionalInserts.any { it.sessionId == sid } }
        SwingAnalysisSessionState.incrementSwingCount("Forehand_Topspin")

        SwingAnalysisSessionState.cancelSession()

        assertFalse(SwingAnalysisSessionState.isSessionActive.value)
        assertEquals(0, SwingAnalysisSessionState.swingCount.value)
        awaitUntil { repo.deletedSessionIds.contains(sid) }
        assertTrue(repo.finalizeCalls.isEmpty())
    }

    private fun awaitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(25)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    private fun RecordingSwingHistoryRepository.getSessionDetailBlocking(sessionId: String) =
        kotlinx.coroutines.runBlocking { getSessionDetail(sessionId) }
}
