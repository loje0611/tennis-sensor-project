package io.github.loje0611.tennisdoc.core.data.repository

import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingMetricsAvg
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwingHistoryRepositoryStartSessionTest {

    @Test
    fun startSessionInsertsLabForehandTopspinProvisional() = runBlocking {
        val repo = RecordingRepo()
        val start = 1_700_000_000_000L

        val sid = repo.startSession(
            sessionType = SessionType.LAB,
            drillType = DrillType.FOREHAND_TOPSPIN,
            startTimeMillis = start,
        )

        val inserted = repo.provisional.single()
        assertEquals(sid, inserted.sessionId)
        assertEquals(SessionType.LAB.name, inserted.sessionType)
        assertEquals(DrillType.FOREHAND_TOPSPIN.name, inserted.drillType)
        assertEquals(start, inserted.startTime)
        assertNull(inserted.endTime)
        assertEquals(0, inserted.totalSwingCount)
        assertTrue(inserted.sessionName.isNotBlank())
    }

    @Test
    fun startSessionMatchDefaultsDrillTypeNull() = runBlocking {
        val repo = RecordingRepo()
        repo.startSession(sessionType = SessionType.MATCH, startTimeMillis = 1L)

        val inserted = repo.provisional.single()
        assertEquals(SessionType.MATCH.name, inserted.sessionType)
        assertNull(inserted.drillType)
    }

    @Test
    fun finalizeSessionPreservesSessionTypeAndDrillType() = runBlocking {
        val repo = RecordingRepo()
        val sid = repo.startSession(
            sessionType = SessionType.LAB,
            drillType = DrillType.FOREHAND_TOPSPIN,
            startTimeMillis = 10L,
        )

        repo.finalizeSession(
            sessionId = sid,
            endTime = 20L,
            totalSwingCount = 3,
            durationMillis = 4_000L,
            fhVolley = 0,
            bhVolley = 0,
            breakdownNormalized = mapOf("forehand topspin" to 3),
        )

        val stored = repo.getSessionDetail(sid)!!.session
        assertEquals(SessionType.LAB.name, stored.sessionType)
        assertEquals(DrillType.FOREHAND_TOPSPIN.name, stored.drillType)
        assertEquals(20L, stored.endTime)
        assertEquals(3, stored.totalSwingCount)
        assertEquals(4_000L, stored.durationMillis)
    }

    private class RecordingRepo : SwingHistoryRepository {
        val provisional = mutableListOf<SwingSessionEntity>()
        private val sessions = mutableMapOf<String, SwingSessionEntity>()

        override fun observeSessions(): Flow<List<SwingSessionEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun generateCsvString(
            sessionId: String?,
            startTimeMillis: Long?,
            endTimeMillis: Long?,
        ): String = ""

        override suspend fun getSessionDetail(sessionId: String): SessionDetailData? {
            val session = sessions[sessionId] ?: return null
            return SessionDetailData(session, emptyList())
        }

        override suspend fun deleteSession(sessionId: String) {
            sessions.remove(sessionId)
        }

        override suspend fun insertProvisionalSession(session: SwingSessionEntity) {
            provisional += session
            sessions[session.sessionId] = session
        }

        override suspend fun finalizeSession(
            sessionId: String,
            endTime: Long,
            totalSwingCount: Int,
            durationMillis: Long,
            fhVolley: Int,
            bhVolley: Int,
            breakdownNormalized: Map<String, Int>,
        ) {
            val current = sessions[sessionId] ?: return
            sessions[sessionId] = current.copy(
                endTime = endTime,
                totalSwingCount = totalSwingCount,
                durationMillis = durationMillis,
                forehandVolleyCount = fhVolley,
                backhandVolleyCount = bhVolley,
            )
        }

        override suspend fun insertSessionWithBreakdown(
            session: SwingSessionEntity,
            breakdown: List<Pair<String, Int>>,
        ) {
            sessions[session.sessionId] = session
        }

        override suspend fun insertMockSession(
            session: SwingSessionEntity,
            breakdownMap: Map<String, Int>,
            events: List<SwingEventEntity>,
        ) {
            sessions[session.sessionId] = session
        }

        override suspend fun insertSwingEvent(event: SwingEventEntity) {}

        override suspend fun getAverageMetrics(
            sessionId: String,
            categoryKey: String,
        ): SwingMetricsAvg? = null

        override suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity> =
            emptyList()

        override suspend fun updateGlobalStatistics(categoryKey: String, metrics: SwingMetrics) {}

        override suspend fun batchUpdateGlobalStatistics(events: List<SwingEventEntity>) {}

        override suspend fun getGlobalAverageMetrics(categoryKey: String): SwingMetrics? = null
    }
}
