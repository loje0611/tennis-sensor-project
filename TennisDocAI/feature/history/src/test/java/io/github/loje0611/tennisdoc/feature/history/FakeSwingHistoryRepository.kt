package io.github.loje0611.tennisdoc.feature.history

import io.github.loje0611.tennisdoc.core.data.db.entity.SessionSwingCountEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingMetricsAvg
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.data.repository.SessionDetailData
import io.github.loje0611.tennisdoc.core.data.repository.SwingHistoryRepository
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSwingHistoryRepository : SwingHistoryRepository {
    private val sessionsFlow = MutableStateFlow<List<SwingSessionEntity>>(emptyList())
    private val breakdowns = mutableMapOf<String, List<SessionSwingCountEntity>>()
    private val events = mutableMapOf<String, MutableList<SwingEventEntity>>()
    
    var csvStringResult: String = ""

    override fun observeSessions(): Flow<List<SwingSessionEntity>> = sessionsFlow

    override suspend fun generateCsvString(
        sessionId: String?,
        startTimeMillis: Long?,
        endTimeMillis: Long?
    ): String = csvStringResult

    override suspend fun getSessionDetail(sessionId: String): SessionDetailData? {
        val session = sessionsFlow.value.find { it.sessionId == sessionId } ?: return null
        return SessionDetailData(
            session = session,
            breakdown = breakdowns[sessionId] ?: emptyList()
        )
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionsFlow.update { list -> list.filter { it.sessionId != sessionId } }
        breakdowns.remove(sessionId)
        events.remove(sessionId)
    }

    override suspend fun insertProvisionalSession(session: SwingSessionEntity) {
        sessionsFlow.update { it + session }
    }

    override suspend fun finalizeSession(
        sessionId: String,
        endTime: Long,
        totalSwingCount: Int,
        durationMillis: Long,
        fhVolley: Int,
        bhVolley: Int,
        breakdownNormalized: Map<String, Int>
    ) {
        sessionsFlow.update { list ->
            list.map {
                if (it.sessionId == sessionId) {
                    it.copy(
                        endTime = endTime,
                        totalSwingCount = totalSwingCount,
                        durationMillis = durationMillis,
                        forehandVolleyCount = fhVolley,
                        backhandVolleyCount = bhVolley
                    )
                } else it
            }
        }
        breakdowns[sessionId] = breakdownNormalized.map {
            SessionSwingCountEntity(sessionId = sessionId, categoryKey = it.key, count = it.value)
        }
    }

    override suspend fun insertSessionWithBreakdown(
        session: SwingSessionEntity,
        breakdown: List<Pair<String, Int>>
    ) {
        sessionsFlow.update { it + session }
        breakdowns[session.sessionId] = breakdown.map {
            SessionSwingCountEntity(sessionId = session.sessionId, categoryKey = it.first, count = it.second)
        }
    }

    override suspend fun insertMockSession(
        session: SwingSessionEntity,
        breakdownMap: Map<String, Int>,
        eventList: List<SwingEventEntity>
    ) {
        sessionsFlow.update { it + session }
        breakdowns[session.sessionId] = breakdownMap.map {
            SessionSwingCountEntity(sessionId = session.sessionId, categoryKey = it.key, count = it.value)
        }
        events.getOrPut(session.sessionId) { mutableListOf() }.addAll(eventList)
    }

    override suspend fun insertSwingEvent(event: SwingEventEntity) {
        events.getOrPut(event.sessionId) { mutableListOf() }.add(event)
    }

    override suspend fun getAverageMetrics(sessionId: String, categoryKey: String): SwingMetricsAvg? {
        return null // Not needed for history view tests right now
    }

    override suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity> {
        return events[sessionId] ?: emptyList()
    }

    override suspend fun updateGlobalStatistics(categoryKey: String, metrics: SwingMetrics) {}

    override suspend fun batchUpdateGlobalStatistics(events: List<SwingEventEntity>) {}

    override suspend fun getGlobalAverageMetrics(categoryKey: String): SwingMetrics? = null

    private val labRecords = mutableMapOf<String, MutableList<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>>()

    override fun getLabRawRecordsForSession(sessionId: String): Flow<List<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>> {
        return MutableStateFlow(labRecords[sessionId] ?: emptyList())
    }

    override suspend fun getLabRawRecordById(recordId: Long): io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity? {
        return labRecords.values.flatten().find { it.id == recordId }
    }

    override suspend fun insertLabRawRecord(record: io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity): Long {
        val assignedId = if (record.id == 0L) (labRecords.values.flatten().maxOfOrNull { it.id } ?: 0L) + 1L else record.id
        val newRecord = record.copy(id = assignedId)
        labRecords.getOrPut(record.sessionId) { mutableListOf() }.add(newRecord)
        return assignedId
    }
}
