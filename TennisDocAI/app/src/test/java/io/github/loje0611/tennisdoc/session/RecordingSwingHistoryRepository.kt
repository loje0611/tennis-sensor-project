package io.github.loje0611.tennisdoc.session

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

/**
 * TASK-030: SessionState 라이프사이클이 repository에 남기는 호출을 관측하기 위한 기록용 Fake.
 */
class RecordingSwingHistoryRepository : SwingHistoryRepository {
    private val lock = Any()
    private val sessionsFlow = MutableStateFlow<List<SwingSessionEntity>>(emptyList())
    private val breakdowns = mutableMapOf<String, List<SessionSwingCountEntity>>()
    private val events = mutableMapOf<String, MutableList<SwingEventEntity>>()

    val provisionalInserts = mutableListOf<SwingSessionEntity>()
    val finalizeCalls = mutableListOf<FinalizeCall>()
    val deletedSessionIds = mutableListOf<String>()
    val insertedEvents = mutableListOf<SwingEventEntity>()

    data class FinalizeCall(
        val sessionId: String,
        val endTime: Long,
        val totalSwingCount: Int,
        val durationMillis: Long,
        val fhVolley: Int,
        val bhVolley: Int,
        val breakdownNormalized: Map<String, Int>,
    )

    override fun observeSessions(): Flow<List<SwingSessionEntity>> = sessionsFlow

    override suspend fun generateCsvString(
        sessionId: String?,
        startTimeMillis: Long?,
        endTimeMillis: Long?,
    ): String = ""

    override suspend fun getSessionDetail(sessionId: String): SessionDetailData? = synchronized(lock) {
        val session = sessionsFlow.value.find { it.sessionId == sessionId } ?: return null
        SessionDetailData(session, breakdowns[sessionId] ?: emptyList())
    }

    override suspend fun deleteSession(sessionId: String) {
        synchronized(lock) {
            deletedSessionIds += sessionId
            sessionsFlow.update { list -> list.filter { it.sessionId != sessionId } }
            breakdowns.remove(sessionId)
            events.remove(sessionId)
        }
    }

    override suspend fun insertProvisionalSession(session: SwingSessionEntity) = synchronized(lock) {
        provisionalInserts += session
        sessionsFlow.update { current ->
            current.filter { it.sessionId != session.sessionId } + session
        }
    }

    override suspend fun finalizeSession(
        sessionId: String,
        endTime: Long,
        totalSwingCount: Int,
        durationMillis: Long,
        fhVolley: Int,
        bhVolley: Int,
        breakdownNormalized: Map<String, Int>,
    ) = synchronized(lock) {
        finalizeCalls += FinalizeCall(
            sessionId = sessionId,
            endTime = endTime,
            totalSwingCount = totalSwingCount,
            durationMillis = durationMillis,
            fhVolley = fhVolley,
            bhVolley = bhVolley,
            breakdownNormalized = breakdownNormalized,
        )
        sessionsFlow.update { list ->
            list.map {
                if (it.sessionId == sessionId) {
                    it.copy(
                        endTime = endTime,
                        totalSwingCount = totalSwingCount,
                        durationMillis = durationMillis,
                        forehandVolleyCount = fhVolley,
                        backhandVolleyCount = bhVolley,
                    )
                } else {
                    it
                }
            }
        }
        breakdowns[sessionId] = breakdownNormalized.map {
            SessionSwingCountEntity(sessionId = sessionId, categoryKey = it.key, count = it.value)
        }
    }

    override suspend fun insertSessionWithBreakdown(
        session: SwingSessionEntity,
        breakdown: List<Pair<String, Int>>,
    ) = synchronized(lock) {
        sessionsFlow.update { it + session }
        breakdowns[session.sessionId] = breakdown.map {
            SessionSwingCountEntity(
                sessionId = session.sessionId,
                categoryKey = it.first,
                count = it.second,
            )
        }
    }

    override suspend fun insertMockSession(
        session: SwingSessionEntity,
        breakdownMap: Map<String, Int>,
        events: List<SwingEventEntity>,
    ) = synchronized(lock) {
        sessionsFlow.update { it + session }
        breakdowns[session.sessionId] = breakdownMap.map {
            SessionSwingCountEntity(sessionId = session.sessionId, categoryKey = it.key, count = it.value)
        }
        this.events.getOrPut(session.sessionId) { mutableListOf() }.addAll(events)
        insertedEvents += events
    }

    override suspend fun insertSwingEvent(event: SwingEventEntity) {
        synchronized(lock) {
            insertedEvents += event
            events.getOrPut(event.sessionId) { mutableListOf() }.add(event)
        }
    }

    override suspend fun getAverageMetrics(sessionId: String, categoryKey: String): SwingMetricsAvg? = null

    override suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity> = synchronized(lock) {
        events[sessionId]?.toList() ?: emptyList()
    }

    override suspend fun updateGlobalStatistics(categoryKey: String, metrics: SwingMetrics) {}

    override suspend fun batchUpdateGlobalStatistics(events: List<SwingEventEntity>) {}

    override suspend fun getGlobalAverageMetrics(categoryKey: String): SwingMetrics? = null

    private val labRecords = mutableMapOf<String, MutableList<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>>()

    override fun getLabRawRecordsForSession(sessionId: String): kotlinx.coroutines.flow.Flow<List<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>> = synchronized(lock) {
        kotlinx.coroutines.flow.MutableStateFlow(labRecords[sessionId]?.toList() ?: emptyList())
    }

    override suspend fun getLabRawRecordById(recordId: Long): io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity? = synchronized(lock) {
        labRecords.values.flatten().find { it.id == recordId }
    }

    override suspend fun insertLabRawRecord(record: io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity): Long = synchronized(lock) {
        val assignedId = if (record.id == 0L) (labRecords.values.flatten().maxOfOrNull { it.id } ?: 0L) + 1L else record.id
        val newRecord = record.copy(id = assignedId)
        labRecords.getOrPut(record.sessionId) { mutableListOf() }.add(newRecord)
        assignedId
    }

    override suspend fun saveAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long) {}
}
