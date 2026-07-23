package com.example.swingsenseai.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.swingsenseai.analysis.SwingClassificationKeys
import com.example.swingsenseai.analysis.SwingMetrics
import com.example.swingsenseai.data.db.SwingSenseDatabase
import com.example.swingsenseai.data.db.dao.GlobalStatisticsDao
import com.example.swingsenseai.data.db.dao.SwingSessionDao
import com.example.swingsenseai.data.db.entity.GlobalStatisticsEntity
import com.example.swingsenseai.data.db.entity.SessionSwingCountEntity
import com.example.swingsenseai.data.db.entity.SwingEventEntity
import com.example.swingsenseai.data.db.entity.SwingMetricsAvg
import com.example.swingsenseai.data.db.entity.SwingSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionDetailData(
    val session: SwingSessionEntity,
    val breakdown: List<SessionSwingCountEntity>,
)

class SwingHistoryRepository(
    private val database: SwingSenseDatabase,
) {

    private val dao: SwingSessionDao = database.swingSessionDao()
    private val globalDao: GlobalStatisticsDao = database.globalStatisticsDao()

    companion object {
        private const val EXPORT_FILE_NAME = "swingsense_export.csv"
        private val CSV_TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    fun observeSessions(): Flow<List<SwingSessionEntity>> = dao.observeSessions()

    /**
     * Room DB의 스윙 이벤트(세션/시간 범위 선택 가능)를 CSV로 변환하여 캐시 디렉토리에 저장한 뒤
     * FileProvider URI를 반환한다. 기본값은 전체 세션·전체 기간. Dispatchers.IO에서 실행.
     *
     * 11-column CSV:
     * Timestamp, SwingType, Power, Spin, Timing, Smoothness, Stability, Consistency,
     * RawAccel(g), RawDuration(ms), RawGyro(dps)
     */
    suspend fun exportDataToCsv(
        context: Context,
        sessionId: String? = null,
        startTimeMillis: Long? = null,
        endTimeMillis: Long? = null,
    ): Uri = withContext(Dispatchers.IO) {
        val events = dao.getSwingEventsForExport(
            sessionId = sessionId,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
        )

        val csv = buildString {
            appendLine("Timestamp,SwingType,Power,Spin,Timing,Smoothness,Stability,Consistency,RawAccel(g),RawDuration(ms),RawGyro(dps)")
            for (e in events) {
                val ts = CSV_TIMESTAMP_FORMAT.format(Date(e.timestampMillis))
                appendLine(
                    String.format(
                        Locale.US,
                        "%s,%s,%d,%d,%d,%d,%d,%d,%.2f,%d,%.1f",
                        ts,
                        e.categoryKey,
                        e.power,
                        e.spin,
                        e.timing,
                        e.fluidity,
                        e.stability,
                        e.consistency,
                        e.rawMaxAccel,
                        e.rawDurationMs,
                        e.rawGyroFollow,
                    )
                )
            }
        }

        val file = File(context.cacheDir, EXPORT_FILE_NAME)
        file.writeText(csv, Charsets.UTF_8)

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    suspend fun getSessionDetail(sessionId: String): SessionDetailData? {
        val session = dao.getSessionById(sessionId) ?: return null
        val breakdown = dao.getBreakdownForSession(sessionId)
        return SessionDetailData(session, breakdown)
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSessionById(sessionId)
    }

    /** BLE 연결 시 임시 세션 레코드를 즉시 삽입. */
    suspend fun insertProvisionalSession(session: SwingSessionEntity) {
        dao.insertSession(session)
    }

    /** 파이프라인 종료 시 세션 확정: 통계 업데이트 + 구종별 breakdown 삽입. */
    suspend fun finalizeSession(
        sessionId: String,
        endTime: Long,
        totalSwingCount: Int,
        durationMillis: Long,
        fhVolley: Int,
        bhVolley: Int,
        breakdownNormalized: Map<String, Int>,
    ) {
        database.withTransaction {
            dao.finalizeSession(
                sessionId = sessionId,
                endTime = endTime,
                totalSwingCount = totalSwingCount,
                durationMillis = durationMillis,
                fhVolley = fhVolley,
                bhVolley = bhVolley,
            )
            dao.insertBreakdownRows(
                breakdownNormalized.map { (key, count) ->
                    SessionSwingCountEntity(sessionId = sessionId, categoryKey = key, count = count)
                },
            )
        }
    }

    suspend fun insertSessionWithBreakdown(
        session: SwingSessionEntity,
        breakdown: List<Pair<String, Int>>,
    ) {
        database.withTransaction {
            dao.insertSession(session)
            dao.insertBreakdownRows(
                breakdown.map { (key, count) ->
                    SessionSwingCountEntity(
                        sessionId = session.sessionId,
                        categoryKey = key,
                        count = count,
                    )
                },
            )
        }
    }

    /** Mock 세션 + breakdown + 개별 SwingEvent 삽입 (Repository 단일 진입점). */
    suspend fun insertMockSession(
        session: SwingSessionEntity,
        breakdownMap: Map<String, Int>,
        events: List<SwingEventEntity>,
    ) {
        database.withTransaction {
            dao.insertSession(session)
            dao.insertBreakdownRows(
                breakdownMap.map { (key, count) ->
                    SessionSwingCountEntity(
                        sessionId = session.sessionId,
                        categoryKey = key,
                        count = count,
                    )
                },
            )
            dao.insertSwingEvents(events)
        }
        batchUpdateGlobalStatistics(events)
    }

    // ── 스윙 이벤트 (운동학 분석) ──────────────────────────────────────────

    suspend fun insertSwingEvent(event: SwingEventEntity) {
        dao.insertSwingEvent(event)
        val key = SwingClassificationKeys.normalize(event.categoryKey)
        updateGlobalStatistics(
            key,
            SwingMetrics(
                power = event.power,
                spin = event.spin,
                timing = event.timing,
                smoothness = event.fluidity,
                stability = event.stability,
                consistency = event.consistency,
            ),
        )
    }

    suspend fun getAverageMetrics(sessionId: String, categoryKey: String): SwingMetricsAvg? {
        return dao.getAverageMetrics(sessionId, categoryKey)
    }

    suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity> {
        return dao.getSwingEventsForSession(sessionId)
    }

    // ── 글로벌 누적 통계 ─────────────────────────────────────────────────

    suspend fun updateGlobalStatistics(categoryKey: String, metrics: SwingMetrics) {
        database.withTransaction {
            val old = globalDao.getByCategory(categoryKey)
            val newCount = (old?.count ?: 0) + 1
            fun runningAvg(oldAvg: Double, newVal: Int): Double =
                oldAvg + (newVal.toDouble() - oldAvg) / newCount

            val updated = GlobalStatisticsEntity(
                categoryKey = categoryKey,
                count = newCount,
                avgPower = runningAvg(old?.avgPower ?: 0.0, metrics.power),
                avgSpin = runningAvg(old?.avgSpin ?: 0.0, metrics.spin),
                avgTiming = runningAvg(old?.avgTiming ?: 0.0, metrics.timing),
                avgFluidity = runningAvg(old?.avgFluidity ?: 0.0, metrics.smoothness),
                avgStability = runningAvg(old?.avgStability ?: 0.0, metrics.stability),
                avgConsistency = runningAvg(old?.avgConsistency ?: 0.0, metrics.consistency),
            )
            globalDao.upsert(updated)
        }
    }

    /**
     * 일괄 이벤트 목록에서 구종별로 합산하여 글로벌 통계를 한 번에 갱신한다.
     * 개별 insertSwingEvent 루프 대비 DB 왕복을 크게 줄인다.
     */
    suspend fun batchUpdateGlobalStatistics(events: List<SwingEventEntity>) {
        val grouped = events.groupBy { SwingClassificationKeys.normalize(it.categoryKey) }
        database.withTransaction {
            for ((key, catEvents) in grouped) {
                val old = globalDao.getByCategory(key)
                val oldCount = old?.count ?: 0
                var avgP = old?.avgPower ?: 0.0
                var avgSp = old?.avgSpin ?: 0.0
                var avgT = old?.avgTiming ?: 0.0
                var avgFl = old?.avgFluidity ?: 0.0
                var avgSt = old?.avgStability ?: 0.0
                var avgCo = old?.avgConsistency ?: 0.0
                var count = oldCount

                for (e in catEvents) {
                    count++
                    avgP += (e.power.toDouble() - avgP) / count
                    avgSp += (e.spin.toDouble() - avgSp) / count
                    avgT += (e.timing.toDouble() - avgT) / count
                    avgFl += (e.fluidity.toDouble() - avgFl) / count
                    avgSt += (e.stability.toDouble() - avgSt) / count
                    avgCo += (e.consistency.toDouble() - avgCo) / count
                }

                globalDao.upsert(
                    GlobalStatisticsEntity(
                        categoryKey = key,
                        count = count,
                        avgPower = avgP, avgSpin = avgSp, avgTiming = avgT,
                        avgFluidity = avgFl, avgStability = avgSt, avgConsistency = avgCo,
                    ),
                )
            }
        }
    }

    suspend fun getGlobalAverageMetrics(categoryKey: String): SwingMetrics? {
        val entity = globalDao.getByCategory(categoryKey) ?: return null
        if (entity.count == 0L) return null
        return SwingMetrics(
            power = entity.avgPower.toInt().coerceIn(0, 100),
            spin = entity.avgSpin.toInt().coerceIn(0, 100),
            timing = entity.avgTiming.toInt().coerceIn(0, 100),
            smoothness = entity.avgFluidity.toInt().coerceIn(0, 100),
            stability = entity.avgStability.toInt().coerceIn(0, 100),
            consistency = entity.avgConsistency.toInt().coerceIn(0, 100),
        )
    }
}
