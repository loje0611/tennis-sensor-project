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
import org.junit.Assert.assertNotNull
import org.junit.Test

class SwingHistoryRepositoryAiReportTest {

    @Test
    fun saveAiCoachReportUpdatesSessionEntity() = runBlocking {
        val repo = FakeAiReportRepo()
        val sid = repo.startSession(
            sessionType = SessionType.MATCH,
            startTimeMillis = 1000L
        )

        val reportJson = """{"summary": "Test Report"}"""
        val generatedAt = 2000L

        repo.saveAiCoachReport(sid, reportJson, generatedAt)

        val detail = repo.getSessionDetail(sid)
        assertNotNull(detail)
        val session = detail!!.session

        assertEquals(reportJson, session.aiCoachReportJson)
        assertEquals(generatedAt, session.aiReportGeneratedAt)
    }

    private class FakeAiReportRepo : SwingHistoryRepository {
        val sessions = mutableMapOf<String, SwingSessionEntity>()

        override fun observeSessions(): Flow<List<SwingSessionEntity>> = MutableStateFlow(sessions.values.toList())

        override suspend fun generateCsvString(sessionId: String?, startTimeMillis: Long?, endTimeMillis: Long?): String = ""

        override suspend fun getSessionDetail(sessionId: String): SessionDetailData? {
            val session = sessions[sessionId] ?: return null
            return SessionDetailData(session, emptyList())
        }

        override suspend fun deleteSession(sessionId: String) {
            sessions.remove(sessionId)
        }

        override suspend fun insertProvisionalSession(session: SwingSessionEntity) {
            sessions[session.sessionId] = session
        }

        override suspend fun finalizeSession(
            sessionId: String, endTime: Long, totalSwingCount: Int, durationMillis: Long,
            fhVolley: Int, bhVolley: Int, breakdownNormalized: Map<String, Int>
        ) {}

        override suspend fun insertSessionWithBreakdown(session: SwingSessionEntity, breakdown: List<Pair<String, Int>>) {}

        override suspend fun insertMockSession(session: SwingSessionEntity, breakdownMap: Map<String, Int>, events: List<SwingEventEntity>) {}

        override suspend fun insertSwingEvent(event: SwingEventEntity) {}

        override suspend fun getAverageMetrics(sessionId: String, categoryKey: String): SwingMetricsAvg? = null

        override suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity> = emptyList()

        override suspend fun updateGlobalStatistics(categoryKey: String, metrics: SwingMetrics) {}

        override suspend fun batchUpdateGlobalStatistics(events: List<SwingEventEntity>) {}

        override suspend fun getGlobalAverageMetrics(categoryKey: String): SwingMetrics? = null

        override fun getLabRawRecordsForSession(sessionId: String): Flow<List<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>> = MutableStateFlow(emptyList())

        override suspend fun getLabRawRecordById(recordId: Long): io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity? = null

        override suspend fun insertLabRawRecord(record: io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity): Long = 1L

        override suspend fun saveAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long) {
            val session = sessions[sessionId] ?: return
            sessions[sessionId] = session.copy(
                aiCoachReportJson = reportJson,
                aiReportGeneratedAt = generatedAt
            )
        }
    }
}
