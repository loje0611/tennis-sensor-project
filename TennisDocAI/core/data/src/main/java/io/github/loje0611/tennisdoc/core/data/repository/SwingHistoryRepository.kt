package io.github.loje0611.tennisdoc.core.data.repository

import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingMetricsAvg
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.model.SessionType
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface SwingHistoryRepository {
    companion object {
        const val EXPORT_FILE_NAME = "swingsense_export.csv"
        const val CSV_HEADER = "Timestamp,SwingType,Power,Spin,Timing,Smoothness,Stability,Consistency,RawAccel(g),RawDuration(ms),RawGyro(dps)"
        const val CSV_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"
    }

    fun observeSessions(): Flow<List<SwingSessionEntity>>

    suspend fun generateCsvString(
        sessionId: String? = null,
        startTimeMillis: Long? = null,
        endTimeMillis: Long? = null,
    ): String

    suspend fun getSessionDetail(sessionId: String): SessionDetailData?

    suspend fun deleteSession(sessionId: String)

    suspend fun startSession(
        sessionType: SessionType = SessionType.MATCH,
        drillType: DrillType? = null,
        startTimeMillis: Long = System.currentTimeMillis(),
    ): String {
        val sid = UUID.randomUUID().toString()
        val session = SwingSessionEntity(
            sessionId = sid,
            sessionName = SwingSessionEntity.formatSessionName(startTimeMillis),
            startTime = startTimeMillis,
            sessionType = sessionType.name,
            drillType = drillType?.name,
        )
        insertProvisionalSession(session)
        return sid
    }

    suspend fun insertProvisionalSession(session: SwingSessionEntity)

    suspend fun finalizeSession(
        sessionId: String,
        endTime: Long,
        totalSwingCount: Int,
        durationMillis: Long,
        fhVolley: Int,
        bhVolley: Int,
        breakdownNormalized: Map<String, Int>,
    )

    suspend fun insertSessionWithBreakdown(
        session: SwingSessionEntity,
        breakdown: List<Pair<String, Int>>,
    )

    suspend fun insertMockSession(
        session: SwingSessionEntity,
        breakdownMap: Map<String, Int>,
        events: List<SwingEventEntity>,
    )

    suspend fun insertSwingEvent(event: SwingEventEntity)

    suspend fun getAverageMetrics(sessionId: String, categoryKey: String): SwingMetricsAvg?

    suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity>

    suspend fun updateGlobalStatistics(categoryKey: String, metrics: SwingMetrics)

    suspend fun batchUpdateGlobalStatistics(events: List<SwingEventEntity>)

    suspend fun getGlobalAverageMetrics(categoryKey: String): SwingMetrics?

    fun getLabRawRecordsForSession(sessionId: String): Flow<List<io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity>>

    suspend fun getLabRawRecordById(recordId: Long): io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity?

    suspend fun insertLabRawRecord(record: io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity): Long
}
