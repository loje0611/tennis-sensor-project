package io.github.loje0611.tennisdoc.sensor

import io.github.loje0611.tennisdoc.core.analysis.SwingKinematicsBuffer
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 스윙 타입별 가상 6축 IMU 센서 데이터(100샘플 @50Hz = 2초)를 생성한다.
 *
 * 생성된 데이터는 [io.github.loje0611.tennisdoc.core.analysis.KinematicAnalyzer]가
 * 6개 지표를 유의미하게 산출할 수 있도록 가우시안 피크 + 사인파 모양으로 구성되며,
 * 프레임별 가우시안 지터(jitter)를 통해 현실적인 저크(jerk) 분산을 만든다.
 */
object MockSwingDataGenerator {

    private const val SAMPLE_COUNT = SwingKinematicsBuffer.CAPACITY

    /**
     * Inference buffer(40샘플) 첫 분류에서 피크를 캡처하도록 앞쪽에 배치.
     * VolleyDetector는 peak 좌우로 duration을 계산하므로, 윈도 중앙이 이상적.
     */
    private const val PEAK_INDEX = 20

    fun generate(swingType: String): List<FloatArray> {
        val key = swingType.lowercase().trim()
        val rng = Random(System.nanoTime())

        return when {
            "forehand" in key && "topspin" in key ->
                buildSamples(rng, peakG = 25f, gyroZPeak = 1800f, gyroDuration = 12f, accelDuration = 8f)
            "backhand" in key && "topspin" in key ->
                buildSamples(rng, peakG = 22f, gyroZPeak = -1600f, gyroDuration = 12f, accelDuration = 8f)
            "forehand" in key && "slice" in key ->
                buildSamples(rng, peakG = 18f, gyroZPeak = 900f, gyroDuration = 14f, accelDuration = 9f)
            "backhand" in key && "slice" in key ->
                buildSamples(rng, peakG = 16f, gyroZPeak = -800f, gyroDuration = 14f, accelDuration = 9f)
            "forehand" in key && "volley" in key ->
                buildSamples(rng, peakG = 10f, gyroZPeak = 400f, gyroDuration = 5f, accelDuration = 3f)
            "backhand" in key && "volley" in key ->
                buildSamples(rng, peakG = 8f, gyroZPeak = -350f, gyroDuration = 5f, accelDuration = 3f)
            else ->
                buildSamples(rng, peakG = 20f, gyroZPeak = 600f, gyroDuration = 10f, accelDuration = 8f)
        }
    }

    /**
     * @param peakG        가속도 피크의 g-force 크기 (≈ 결과 RawAccel(g) 값과 근사)
     * @param gyroZPeak    자이로 Z축 피크 (dps). 부호로 포핸드/백핸드를 구분한다.
     * @param gyroDuration 자이로 가우시안 시그마 (샘플 단위). 작을수록 짧은 임팩트.
     * @param accelDuration 가속도 가우시안 시그마 (샘플 단위).
     */
    private fun buildSamples(
        rng: Random,
        peakG: Float,
        gyroZPeak: Float,
        gyroDuration: Float,
        accelDuration: Float,
    ): List<FloatArray> {
        val jrng = java.util.Random(rng.nextLong())

        return List(SAMPLE_COUNT) { i ->
            val t = (i - PEAK_INDEX).toFloat()
            val envFactor = gaussian(t, accelDuration)
            val gyroEnv = gaussian(t, gyroDuration)
            val phase = i.toFloat() / SAMPLE_COUNT * Math.PI.toFloat() * 2f

            // ±50% 가우시안 멀티플리케이티브 지터 → 프레임간 magnitude 변동 → jerk 분산 생성
            val envJitter = 1f + jrng.nextGaussian().toFloat() * 0.5f
            val accelEnv = (envFactor * peakG * 9.81f * envJitter).coerceAtLeast(0f)

            // 축별 가우시안 노이즈 — VolleyDetector threshold 안정성을 위해 소폭으로 제한
            val aN = { jrng.nextGaussian().toFloat() * 2f }
            val gN = { jrng.nextGaussian().toFloat() * 5f }

            floatArrayOf(
                accelEnv * 0.35f + sin(phase) * 0.8f + aN(),
                accelEnv * 0.45f + sin(phase * 1.3f) * 0.6f + aN(),
                9.81f + accelEnv * 0.82f + aN(),
                gyroEnv * gyroZPeak * 0.25f + gN(),
                gyroEnv * gyroZPeak * 0.15f + gN(),
                gyroEnv * gyroZPeak + gN(),
            )
        }
    }

    private fun gaussian(t: Float, sigma: Float): Float =
        exp(-(t * t) / (2f * sigma * sigma))
}
