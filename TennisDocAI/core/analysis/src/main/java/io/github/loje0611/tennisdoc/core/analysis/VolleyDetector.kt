package io.github.loje0611.tennisdoc.core.analysis

import io.github.loje0611.tennisdoc.core.model.SwingClassificationKeys

/**
 * Two-Stage 아키텍처의 Stage 1: 물리 기반 발리 판별 게이트키퍼.
 *
 * 800ms(40샘플 @50Hz) 센서 윈도우를 입력받아 3단계 휴리스틱으로 분석한다.
 * - 발리(펀치/블로킹)일 경우 → [SwingClassificationKeys.FOREHAND_VOLLEY] 또는 [BACKHAND_VOLLEY] 반환
 * - 스트로크(풀스윙)일 경우 → `null` 반환 → Stage 2(AI 모델)로 위임
 *
 * 동적 임계값은 [CalibrationStore][io.github.loje0611.tennisdoc.data.repository.CalibrationStore]에서
 * 읽어 [accelThresholdSq], [maxVolleyDurationMs], [gyroFollowThroughThresholdSq] 에 반영된다.
 *
 * 인스턴스 기반이므로 테스트/멀티 파이프라인에서 독립적으로 사용할 수 있다.
 */
class VolleyDetector(
    accelThresholdSq: Float = DEFAULT_ACCEL_THRESHOLD_SQ,
    maxVolleyDurationMs: Long = DEFAULT_MAX_VOLLEY_DURATION_MS,
    gyroFollowThroughThresholdSq: Float = DEFAULT_GYRO_FOLLOW_THROUGH_THRESHOLD_SQ,
) {

    // ── 동적 임계값 (서비스에서 CalibrationStore Flow 구독 후 갱신) ────

    @Volatile var accelThresholdSq = accelThresholdSq
    @Volatile var maxVolleyDurationMs = maxVolleyDurationMs
    @Volatile var gyroFollowThroughThresholdSq = gyroFollowThroughThresholdSq

    /** 마지막 판별 사이클의 원시 데이터 요약. 서비스가 읽어 디버그 콘솔로 전달. */
    @Volatile var lastDebugInfo: String = ""
        private set

    /** 마지막 판별 사이클의 구조화된 원시 값 — DB 저장용. */
    @Volatile var lastPeakAccelSq: Float = 0f
        private set
    @Volatile var lastDurationMs: Long = 0L
        private set
    @Volatile var lastAvgGyroSq: Float = 0f
        private set

    private data class FollowThroughStats(
        val avgGyroSq: Float,
        val followCount: Int,
    )

    // ── Public API ────────────────────────────────────────────────────────

    fun detect(window: Collection<FloatArray>): String? {
        if (window.size < EdgeImpulseInputSpec.WINDOW_SAMPLES) return null

        val samples = if (window is List<*>) {
            @Suppress("UNCHECKED_CAST")
            window as List<FloatArray>
        } else {
            window.toList()
        }

        return detectFromSamples(samples)
    }

    fun detectFromFlat(flat: FloatArray, sampleCount: Int, axesPerSample: Int): String? {
        if (sampleCount < EdgeImpulseInputSpec.WINDOW_SAMPLES) return null

        lastPeakAccelSq = 0f
        lastDurationMs = 0L
        lastAvgGyroSq = 0f

        val accelSqArray = FloatArray(sampleCount)
        var peakIdx = 0
        var peakAccelSq = 0f

        for (i in 0 until sampleCount) {
            val base = i * axesPerSample
            val ax = flat[base]; val ay = flat[base + 1]; val az = flat[base + 2]
            val sq = ax * ax + ay * ay + az * az
            accelSqArray[i] = sq
            if (sq > peakAccelSq) {
                peakAccelSq = sq
                peakIdx = i
            }
        }

        lastPeakAccelSq = peakAccelSq

        val durationMs = computeImpactDurationMs(accelSqArray, peakIdx)
        lastDurationMs = durationMs

        val followStats = computeFollowThroughStatsFromFlat(
            flat = flat,
            sampleCount = sampleCount,
            axesPerSample = axesPerSample,
            peakIdx = peakIdx,
        )
        val avgGyroSq = followStats.avgGyroSq
        lastAvgGyroSq = avgGyroSq

        if (peakAccelSq < MIN_IMPACT_ACCEL_SQ) {
            lastDebugInfo = "[VOLLEY] Skip: PeakAccel²=%.1f < MIN(%.1f)"
                .format(peakAccelSq, MIN_IMPACT_ACCEL_SQ)
            return null
        }

        if (durationMs > maxVolleyDurationMs) {
            lastDebugInfo = "[VOLLEY] Stroke: Dur=%dms > MAX(%dms) | PeakAccel²=%.1f"
                .format(durationMs, maxVolleyDurationMs, peakAccelSq)
            return null
        }

        if (followStats.followCount < MIN_FOLLOW_THROUGH_SAMPLES) {
            lastDebugInfo = "[VOLLEY] Skip: FollowSamples=%d < MIN(%d)"
                .format(followStats.followCount, MIN_FOLLOW_THROUGH_SAMPLES)
            return null
        }

        if (avgGyroSq > gyroFollowThroughThresholdSq) {
            lastDebugInfo = "[VOLLEY] Stroke: AvgGyro²=%.0f > THR(%.0f) | Accel²=%.1f Dur=%dms"
                .format(avgGyroSq, gyroFollowThroughThresholdSq, peakAccelSq, durationMs)
            return null
        }

        val peakBase = peakIdx * axesPerSample
        val gyroZ = flat[peakBase + 5]
        val label = if (gyroZ >= 0f) SwingClassificationKeys.FOREHAND_VOLLEY
        else SwingClassificationKeys.BACKHAND_VOLLEY

        lastDebugInfo = "[VOLLEY] >> %s | Accel²=%.1f Dur=%dms AvgGyro²=%.0f GzPeak=%.1f"
            .format(label, peakAccelSq, durationMs, avgGyroSq, gyroZ)
        return label
    }

    private fun detectFromSamples(samples: List<FloatArray>): String? {
        lastPeakAccelSq = 0f
        lastDurationMs = 0L
        lastAvgGyroSq = 0f

        val accelSqArray = FloatArray(samples.size)
        var peakIdx = 0
        var peakAccelSq = 0f

        for (i in samples.indices) {
            val s = samples[i]
            val ax = s[0]; val ay = s[1]; val az = s[2]
            val sq = ax * ax + ay * ay + az * az
            accelSqArray[i] = sq
            if (sq > peakAccelSq) {
                peakAccelSq = sq
                peakIdx = i
            }
        }

        lastPeakAccelSq = peakAccelSq

        val durationMs = computeImpactDurationMs(accelSqArray, peakIdx)
        lastDurationMs = durationMs

        val followStats = computeFollowThroughStatsFromSamples(
            samples = samples,
            peakIdx = peakIdx,
        )
        val avgGyroSq = followStats.avgGyroSq
        lastAvgGyroSq = avgGyroSq

        if (peakAccelSq < MIN_IMPACT_ACCEL_SQ) {
            lastDebugInfo = "[VOLLEY] Skip: PeakAccel²=%.1f < MIN(%.1f)"
                .format(peakAccelSq, MIN_IMPACT_ACCEL_SQ)
            return null
        }

        if (durationMs > maxVolleyDurationMs) {
            lastDebugInfo = "[VOLLEY] Stroke: Dur=%dms > MAX(%dms) | PeakAccel²=%.1f"
                .format(durationMs, maxVolleyDurationMs, peakAccelSq)
            return null
        }

        if (followStats.followCount < MIN_FOLLOW_THROUGH_SAMPLES) {
            lastDebugInfo = "[VOLLEY] Skip: FollowSamples=%d < MIN(%d)"
                .format(followStats.followCount, MIN_FOLLOW_THROUGH_SAMPLES)
            return null
        }

        if (avgGyroSq > gyroFollowThroughThresholdSq) {
            lastDebugInfo = "[VOLLEY] Stroke: AvgGyro²=%.0f > THR(%.0f) | Accel²=%.1f Dur=%dms"
                .format(avgGyroSq, gyroFollowThroughThresholdSq, peakAccelSq, durationMs)
            return null
        }

        val label = classifyVolleyHand(samples[peakIdx])
        val gyroZ = samples[peakIdx][5]
        lastDebugInfo = "[VOLLEY] >> %s | Accel²=%.1f Dur=%dms AvgGyro²=%.0f GzPeak=%.1f"
            .format(label, peakAccelSq, durationMs, avgGyroSq, gyroZ)
        return label
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private fun computeImpactDurationMs(accelSq: FloatArray, peakIdx: Int): Long {
        var left = peakIdx
        while (left > 0 && accelSq[left - 1] >= accelThresholdSq) left--

        var right = peakIdx
        while (right < accelSq.size - 1 && accelSq[right + 1] >= accelThresholdSq) right++

        return (right - left + 1).toLong() * SAMPLE_INTERVAL_MS
    }

    private fun classifyVolleyHand(impactSample: FloatArray): String {
        val gyroZ = impactSample[5]
        return if (gyroZ >= 0f) {
            SwingClassificationKeys.FOREHAND_VOLLEY
        } else {
            SwingClassificationKeys.BACKHAND_VOLLEY
        }
    }

    private fun computeFollowThroughStatsFromFlat(
        flat: FloatArray,
        sampleCount: Int,
        axesPerSample: Int,
        peakIdx: Int,
    ): FollowThroughStats {
        val followStart = (peakIdx + FOLLOW_THROUGH_OFFSET_SAMPLES).coerceAtMost(sampleCount)
        val followCount = sampleCount - followStart
        if (followCount <= 0) {
            val peakBase = peakIdx * axesPerSample
            val gx = flat[peakBase + 3]; val gy = flat[peakBase + 4]; val gz = flat[peakBase + 5]
            val peakGyroSq = gx * gx + gy * gy + gz * gz
            return FollowThroughStats(avgGyroSq = peakGyroSq, followCount = 0)
        }

        var gyroSqSum = 0f
        for (i in followStart until sampleCount) {
            val base = i * axesPerSample
            val gx = flat[base + 3]; val gy = flat[base + 4]; val gz = flat[base + 5]
            gyroSqSum += gx * gx + gy * gy + gz * gz
        }
        return FollowThroughStats(avgGyroSq = gyroSqSum / followCount, followCount = followCount)
    }

    private fun computeFollowThroughStatsFromSamples(
        samples: List<FloatArray>,
        peakIdx: Int,
    ): FollowThroughStats {
        val followStart = (peakIdx + FOLLOW_THROUGH_OFFSET_SAMPLES).coerceAtMost(samples.size)
        val followCount = samples.size - followStart
        if (followCount <= 0) {
            val peak = samples[peakIdx]
            val peakGyroSq = peak[3] * peak[3] + peak[4] * peak[4] + peak[5] * peak[5]
            return FollowThroughStats(avgGyroSq = peakGyroSq, followCount = 0)
        }

        var gyroSqSum = 0f
        for (i in followStart until samples.size) {
            val s = samples[i]
            val gx = s[3]; val gy = s[4]; val gz = s[5]
            gyroSqSum += gx * gx + gy * gy + gz * gz
        }
        return FollowThroughStats(avgGyroSq = gyroSqSum / followCount, followCount = followCount)
    }

    companion object {
        const val DEFAULT_ACCEL_THRESHOLD_SQ = 385f
        const val DEFAULT_MAX_VOLLEY_DURATION_MS = 350L
        const val DEFAULT_GYRO_FOLLOW_THROUGH_THRESHOLD_SQ = 1440000f

        const val FOLLOW_THROUGH_OFFSET_SAMPLES = 3
        const val MIN_FOLLOW_THROUGH_SAMPLES = 5
        const val MIN_IMPACT_ACCEL_SQ = 200f
        private const val SAMPLE_INTERVAL_MS = 20L
    }
}
