package com.example.swingsenseai.analysis

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 센서 데이터를 분석하여 6개 스윙 지표([SwingMetrics])를 반환한다.
 *
 * 입력: 50Hz 샘플 리스트, 각 샘플 [ax, ay, az, gx, gy, gz].
 * 출력: 각 지표 0~100 정수 스케일.
 *
 * [powerMax], [spinMax] 는 [CalibrationStore][com.example.swingsenseai.data.repository.CalibrationStore]
 * 에서 동적으로 갱신된다.
 *
 * 인스턴스 기반이므로 테스트/멀티 파이프라인에서 독립적으로 사용할 수 있다.
 */
class KinematicAnalyzer(
    powerMax: Float = DEFAULT_POWER_MAX,
    spinMax: Float = DEFAULT_SPIN_MAX,
    smoothnessWorstVariance: Float = DEFAULT_SMOOTHNESS_WORST_VARIANCE,
) {

    // ── 동적 정규화 상한 (서비스에서 CalibrationStore Flow 구독 후 갱신) ──

    @Volatile var powerMax = powerMax
    @Volatile var spinMax = spinMax
    @Volatile var smoothnessWorstVariance = smoothnessWorstVariance

    /** 마지막 분석 사이클의 원시 데이터 요약. */
    @Volatile var lastDebugInfo: String = ""
        private set

    /** 마지막 분석에서 계산된 jerk 분산값 (디버그 콘솔용). */
    @Volatile var lastJerkVariance: Float = 0f
        private set

    fun analyze(samples: List<FloatArray>, impactHintIndex: Int? = null): SwingMetrics {
        if (samples.isEmpty()) return SwingMetrics(0, 0, 0, 0, 0, 50)

        val accelMags = FloatArray(samples.size)
        val gyroZ = FloatArray(samples.size)
        val gyroX = FloatArray(samples.size)
        val gyroY = FloatArray(samples.size)

        for (i in samples.indices) {
            val s = samples[i]
            val ax = s[0]; val ay = s[1]; val az = s[2]
            accelMags[i] = sqrt(ax * ax + ay * ay + az * az)
            gyroX[i] = s[3]
            gyroY[i] = s[4]
            gyroZ[i] = s[5]
        }

        val globalPeakIdx = impactHintIndex
            ?: accelMags.indices.maxByOrNull { accelMags[it] }
            ?: return SwingMetrics(0, 0, 0, 0, 0, 50)

        val winStart = (globalPeakIdx - ANALYSIS_HALF_WINDOW).coerceAtLeast(0)
        val winEnd = (globalPeakIdx + ANALYSIS_HALF_WINDOW).coerceAtMost(samples.size - 1)

        val wAccelMags = accelMags.sliceArray(winStart..winEnd)
        val wGyroX = gyroX.sliceArray(winStart..winEnd)
        val wGyroY = gyroY.sliceArray(winStart..winEnd)
        val wGyroZ = gyroZ.sliceArray(winStart..winEnd)
        val localPeakIdx = globalPeakIdx - winStart

        val maxAccelMag = wAccelMags.max()
        val maxAccelG = maxAccelMag / 9.81f
        val power = normalizeMinMax(maxAccelG, POWER_MIN, powerMax)

        val maxGyroZ = wGyroZ.maxOf { abs(it) }
        val spin = normalizeMinMax(maxGyroZ, SPIN_MIN, spinMax)

        val timing = computeTiming(wAccelMags, localPeakIdx)
        val smoothness = computeSmoothness(wAccelMags)
        val stability = computeStability(wAccelMags, wGyroX, wGyroY, localPeakIdx)
        val consistency = computeConsistency(wAccelMags, localPeakIdx)

        lastDebugInfo =
            "[METRICS] P:%d S:%d T:%d Sm:%d St:%d C:%d | MaxAccel=%.2fg MaxGyroZ=%.1f Var(j)=%.4f (PwrMax=%.1fg SpnMax=%.1f SmWorst=%.1f)"
                .format(
                    power, spin, timing, smoothness, stability, consistency,
                    maxAccelG, maxGyroZ, lastJerkVariance, powerMax, spinMax, smoothnessWorstVariance,
                )

        return SwingMetrics(
            power = power,
            spin = spin,
            timing = timing,
            smoothness = smoothness,
            stability = stability,
            consistency = consistency,
        )
    }

    private fun computeTiming(accelMags: FloatArray, peakIdx: Int): Int {
        if (accelMags.size < 2) return 50

        val mean = accelMags.average().toFloat()
        val threshold = mean * 1.5f

        var thresholdIdx = -1
        for (i in 0 until peakIdx) {
            if (accelMags[i] >= threshold) {
                thresholdIdx = i
                break
            }
        }
        if (thresholdIdx < 0) {
            for (i in accelMags.indices) {
                if (accelMags[i] >= threshold) {
                    thresholdIdx = i
                    break
                }
            }
        }
        if (thresholdIdx < 0) return 50

        val deltaMs = (peakIdx - thresholdIdx) * SAMPLE_INTERVAL_MS
        return normalizeInverse(deltaMs, TIMING_BEST_MS, TIMING_WORST_MS)
    }

    private fun computeSmoothness(accelMags: FloatArray): Int {
        if (accelMags.size < 3) return 50

        val jerks = FloatArray(accelMags.size - 1) { i ->
            (accelMags[i + 1] - accelMags[i]) / SAMPLE_INTERVAL_MS
        }

        val mean = jerks.average().toFloat()
        val variance = jerks.map { (it - mean) * (it - mean) }.average().toFloat()
        lastJerkVariance = variance

        return normalizeInverse(variance, SMOOTHNESS_VARIANCE_LOW, smoothnessWorstVariance)
    }

    private fun computeStability(
        accelMags: FloatArray,
        gyroX: FloatArray,
        gyroY: FloatArray,
        peakIdx: Int,
    ): Int {
        if (accelMags.size < 5) return 50

        val rangeStart = (peakIdx - 5).coerceAtLeast(0)
        val rangeEnd = (peakIdx + 5).coerceAtMost(accelMags.size - 1)

        val gxSlice = gyroX.slice(rangeStart..rangeEnd)
        val gySlice = gyroY.slice(rangeStart..rangeEnd)

        val stdGx = standardDeviation(gxSlice)
        val stdGy = standardDeviation(gySlice)
        val combinedStd = (stdGx + stdGy) / 2f

        return normalizeInverse(combinedStd, STABILITY_STD_LOW, STABILITY_STD_HIGH)
    }

    private fun computeConsistency(accelMags: FloatArray, peakIdx: Int): Int {
        if (accelMags.size < 10) return 50

        val halfWindow = minOf(peakIdx, accelMags.size - 1 - peakIdx)
        if (halfWindow < 3) return 50

        val pre = FloatArray(halfWindow) { i -> accelMags[peakIdx - halfWindow + i] }
        val post = FloatArray(halfWindow) { i -> accelMags[peakIdx + 1 + i] }
        pre.reverse()

        val correlation = pearsonCorrelation(pre, post)
        return ((correlation + 1f) / 2f * 100f).toInt().coerceIn(0, 100)
    }

    companion object {
        const val DEFAULT_POWER_MAX = 27f
        const val DEFAULT_SPIN_MAX = 2100f
        const val DEFAULT_SMOOTHNESS_WORST_VARIANCE = 50f

        private const val POWER_MIN = 0f
        private const val SPIN_MIN = 0f
        private const val TIMING_BEST_MS = 0f
        private const val TIMING_WORST_MS = 400f
        private const val SMOOTHNESS_VARIANCE_LOW = 0.01f
        private const val STABILITY_STD_LOW = 0f
        private const val STABILITY_STD_HIGH = 300f
        private const val SAMPLE_INTERVAL_MS = 20f
        private const val ANALYSIS_HALF_WINDOW = 30

        private fun pearsonCorrelation(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            val n = a.size
            val meanA = a.average().toFloat()
            val meanB = b.average().toFloat()
            var cov = 0f; var varA = 0f; var varB = 0f
            for (i in 0 until n) {
                val da = a[i] - meanA
                val db = b[i] - meanB
                cov += da * db
                varA += da * da
                varB += db * db
            }
            val denom = sqrt(varA * varB)
            return if (denom < 1e-9f) 0f else cov / denom
        }

        private fun normalizeMinMax(value: Float, min: Float, max: Float): Int {
            if (max <= min) return 0
            val ratio = ((value - min) / (max - min)).coerceIn(0f, 1f)
            return (ratio * 100).toInt()
        }

        private fun normalizeInverse(value: Float, best: Float, worst: Float): Int {
            if (worst <= best) return 50
            val ratio = ((worst - value) / (worst - best)).coerceIn(0f, 1f)
            return (ratio * 100).toInt()
        }

        private fun standardDeviation(values: List<Float>): Float {
            if (values.size < 2) return 0f
            val mean = values.average().toFloat()
            val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
            return sqrt(variance)
        }
    }
}
