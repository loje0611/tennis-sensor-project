package io.github.loje0611.tennisdoc.analysis

import kotlin.math.sqrt

/**
 * 원시 물리 텔레메트리 3요소.
 * 서비스와 Mock 경로 모두에서 동일한 방법으로 계산하기 위해 공유 유틸로 분리.
 */
data class RawSwingTelemetry(
    val maxAccelG: Float,
    val durationMs: Int,
    val gyroFollowDps: Float,
) {
    fun withFallback(other: RawSwingTelemetry) = RawSwingTelemetry(
        maxAccelG = if (maxAccelG > 0f) maxAccelG else other.maxAccelG,
        durationMs = if (durationMs > 0) durationMs else other.durationMs,
        gyroFollowDps = if (gyroFollowDps > 0f) gyroFollowDps else other.gyroFollowDps,
    )

    companion object {
        private const val SAMPLE_INTERVAL_MS = 20

        fun fromSnapshot(
            snapshot: List<FloatArray>,
            accelThresholdSq: Float = VolleyDetector.DEFAULT_ACCEL_THRESHOLD_SQ,
        ): RawSwingTelemetry {
            if (snapshot.isEmpty()) return RawSwingTelemetry(0f, 0, 0f)

            val accelSq = FloatArray(snapshot.size)
            var peakIdx = 0
            var peakAccelSq = 0f
            for (i in snapshot.indices) {
                val s = snapshot[i]
                val ax = s[0]; val ay = s[1]; val az = s[2]
                val sq = ax * ax + ay * ay + az * az
                accelSq[i] = sq
                if (sq > peakAccelSq) {
                    peakAccelSq = sq
                    peakIdx = i
                }
            }

            var left = peakIdx
            while (left > 0 && accelSq[left - 1] >= accelThresholdSq) left--
            var right = peakIdx
            while (right < accelSq.size - 1 && accelSq[right + 1] >= accelThresholdSq) right++
            val durationMs = ((right - left + 1) * SAMPLE_INTERVAL_MS)
                .coerceAtLeast(SAMPLE_INTERVAL_MS)

            val followStart = (peakIdx + VolleyDetector.FOLLOW_THROUGH_OFFSET_SAMPLES)
                .coerceAtMost(snapshot.size)
            val followCount = snapshot.size - followStart
            val avgGyroSq = if (followCount > 0) {
                var sum = 0f
                for (i in followStart until snapshot.size) {
                    val s = snapshot[i]
                    sum += s[3] * s[3] + s[4] * s[4] + s[5] * s[5]
                }
                sum / followCount
            } else {
                val p = snapshot[peakIdx]
                p[3] * p[3] + p[4] * p[4] + p[5] * p[5]
            }

            return RawSwingTelemetry(
                maxAccelG = sqrt(peakAccelSq.coerceAtLeast(0f)) / 9.81f,
                durationMs = durationMs,
                gyroFollowDps = sqrt(avgGyroSq.coerceAtLeast(0f)),
            )
        }
    }
}
