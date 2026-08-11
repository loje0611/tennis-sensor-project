package io.github.loje0611.tennisdoc.core.data.repository

import androidx.room.withTransaction
import io.github.loje0611.tennisdoc.core.model.SwingClassificationKeys
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import io.github.loje0611.tennisdoc.core.data.db.TennisDocDatabase
import io.github.loje0611.tennisdoc.core.data.db.dao.GlobalStatisticsDao
import io.github.loje0611.tennisdoc.core.data.db.dao.SwingSessionDao
import io.github.loje0611.tennisdoc.core.data.db.entity.GlobalStatisticsEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SessionSwingCountEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingMetricsAvg
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import javax.inject.Inject

data class SessionDetailData(
    val session: SwingSessionEntity,
    val breakdown: List<SessionSwingCountEntity>,
)

class SwingHistoryRepositoryImpl @Inject constructor(
    private val database: TennisDocDatabase,
) : SwingHistoryRepository {

    private val dao: SwingSessionDao = database.swingSessionDao()
    private val globalDao: GlobalStatisticsDao = database.globalStatisticsDao()

    override fun observeSessions(): Flow<List<SwingSessionEntity>> = dao.observeSessions()

    /**
     * Room DB의 스윙 이벤트(세션/시간 범위 선택 가능)를 CSV 문자열로 생성하여 반환한다.
     * Android Context/Uri/FileProvider 의존성을 갖지 않는다.
     */
    override suspend fun generateCsvString(
        sessionId: String?,
        startTimeMillis: Long?,
        endTimeMillis: Long?,
    ): String = withContext(Dispatchers.IO) {
        val events = dao.getSwingEventsForExport(
            sessionId = sessionId,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
        )

        buildString {
            appendLine(SwingHistoryRepository.CSV_HEADER)
            for (e in events) {
                val ts = SwingHistoryRepository.CSV_TIMESTAMP_FORMAT.format(Date(e.timestampMillis))
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
    }

    override suspend fun getSessionDetail(sessionId: String): SessionDetailData? {
        val session = dao.getSessionById(sessionId) ?: return null
        val breakdown = dao.getBreakdownForSession(sessionId)
        return SessionDetailData(session, breakdown)
    }

    override suspend fun deleteSession(sessionId: String) {
        dao.deleteSessionById(sessionId)
    }

    /** BLE 연결 시 임시 세션 레코드를 즉시 삽입. */
    override suspend fun insertProvisionalSession(session: SwingSessionEntity) {
        dao.insertSession(session)
    }

    /** 파이프라인 종료 시 세션 확정: 통계 업데이트 + 구종별 breakdown 삽입. */
    override suspend fun finalizeSession(
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

    override suspend fun insertSessionWithBreakdown(
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
    override suspend fun insertMockSession(
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

    override suspend fun insertSwingEvent(event: SwingEventEntity) {
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

    override suspend fun getAverageMetrics(sessionId: String, categoryKey: String): SwingMetricsAvg? {
        return dao.getAverageMetrics(sessionId, categoryKey)
    }

    override suspend fun getSwingEventsForSession(sessionId: String): List<SwingEventEntity> {
        return dao.getSwingEventsForSession(sessionId)
    }

    // ── 글로벌 누적 통계 ─────────────────────────────────────────────────

    override suspend fun updateGlobalStatistics(categoryKey: String, metrics: SwingMetrics) {
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
    override suspend fun batchUpdateGlobalStatistics(events: List<SwingEventEntity>) {
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

    override suspend fun getGlobalAverageMetrics(categoryKey: String): SwingMetrics? {
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
