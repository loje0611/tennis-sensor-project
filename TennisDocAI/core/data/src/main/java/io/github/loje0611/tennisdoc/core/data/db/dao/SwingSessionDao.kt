package io.github.loje0611.tennisdoc.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.loje0611.tennisdoc.core.data.db.entity.SessionSwingCountEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingMetricsAvg
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SwingSessionDao {

    @Query("SELECT * FROM swing_sessions ORDER BY startTime DESC")
    fun observeSessions(): Flow<List<SwingSessionEntity>>

    @Query("SELECT * FROM swing_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SwingSessionEntity?

    @Query("SELECT * FROM session_swing_counts WHERE sessionId = :sessionId ORDER BY count DESC")
    suspend fun getBreakdownForSession(sessionId: String): List<SessionSwingCountEntity>

    @Query("DELETE FROM swing_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Insert
    suspend fun insertSession(session: SwingSessionEntity)

    @Insert
    suspend fun insertBreakdownRows(rows: List<SessionSwingCountEntity>)

    @Query("SELECT COUNT(*) FROM swing_sessions")
    suspend fun countSessions(): Int

    @Query(
        """
        UPDATE swing_sessions SET
            endTime = :endTime,
            totalSwingCount = :totalSwingCount,
            durationMillis = :durationMillis,
            forehandVolleyCount = :fhVolley,
            backhandVolleyCount = :bhVolley
        WHERE sessionId = :sessionId
        """
    )
    suspend fun finalizeSession(
        sessionId: String,
        endTime: Long,
        totalSwingCount: Int,
        durationMillis: Long,
        fhVolley: Int,
        bhVolley: Int,
    )

    @Query("UPDATE swing_sessions SET aiCoachReportJson = :reportJson, aiReportGeneratedAt = :generatedAt WHERE sessionId = :sessionId")
    suspend fun updateAiCoachReport(sessionId: String, reportJson: String, generatedAt: Long)

    // ── 스윙 이벤트 (운동학 분석) ──────────────────────────────────────────

    @Insert
    suspend fun insertSwingEvent(event: SwingEventEntity)

    @Insert
    suspend fun insertSwingEvents(events: List<SwingEventEntity>)

    @Query(
        """
        SELECT AVG(power) AS power, AVG(spin) AS spin, AVG(timing) AS timing,
               AVG(fluidity) AS fluidity, AVG(stability) AS stability, AVG(consistency) AS consistency
        FROM swing_events
        WHERE sessionId = :sessionId AND categoryKey = :categoryKey
        """
    )
    suspend fun getAverageMetrics(sessionId: String, categoryKey: String): SwingMetricsAvg?

    @Query("SELECT * FROM swing_events WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity>

    @Query(
        """
        SELECT * FROM swing_events
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
          AND (:startTimeMillis IS NULL OR timestampMillis >= :startTimeMillis)
          AND (:endTimeMillis IS NULL OR timestampMillis <= :endTimeMillis)
        ORDER BY timestampMillis ASC
        """
    )
    suspend fun getSwingEventsForExport(
        sessionId: String?,
        startTimeMillis: Long?,
        endTimeMillis: Long?,
    ): List<SwingEventEntity>
}
