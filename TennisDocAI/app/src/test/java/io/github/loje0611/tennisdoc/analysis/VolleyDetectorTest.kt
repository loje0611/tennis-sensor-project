package io.github.loje0611.tennisdoc.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VolleyDetectorTest {

    private lateinit var detector: VolleyDetector

    @Before
    fun setUp() {
        detector = VolleyDetector()
    }

    private fun sample(
        ax: Float = 0f, ay: Float = 0f, az: Float = 0f,
        gx: Float = 0f, gy: Float = 0f, gz: Float = 0f,
    ) = floatArrayOf(ax, ay, az, gx, gy, gz)

    private fun makeWindow(size: Int = EdgeImpulseInputSpec.WINDOW_SAMPLES, builder: (Int) -> FloatArray): List<FloatArray> =
        List(size) { builder(it) }

    // ── 발리 정상 감지 ───────────────────────────────────────────────────

    @Test
    fun `short sharp impact with low follow-through detects as volley`() {
        val peakIdx = 20
        val window = makeWindow { i ->
            if (i in (peakIdx - 2)..(peakIdx + 2)) {
                sample(ax = 30f, ay = 10f, az = 5f, gz = 50f)
            } else {
                sample(ax = 1f, ay = 1f, az = 1f, gx = 5f, gy = 5f, gz = 5f)
            }
        }

        val result = detector.detect(window)
        assertNotNull("짧은 임팩트 + 낮은 Follow-through → 발리 감지해야 함", result)
        assertTrue(
            result == SwingClassificationKeys.FOREHAND_VOLLEY ||
                result == SwingClassificationKeys.BACKHAND_VOLLEY,
        )
    }

    @Test
    fun `forehand volley detected when gyroZ is positive at impact`() {
        val peakIdx = 20
        val window = makeWindow { i ->
            if (i in (peakIdx - 1)..(peakIdx + 1)) {
                sample(ax = 25f, ay = 10f, az = 5f, gz = 100f)
            } else {
                sample(ax = 1f, ay = 0.5f, az = 0.5f, gz = 2f)
            }
        }

        val result = detector.detect(window)
        assertEquals(SwingClassificationKeys.FOREHAND_VOLLEY, result)
    }

    @Test
    fun `backhand volley detected when gyroZ is negative at impact`() {
        val peakIdx = 20
        val window = makeWindow { i ->
            if (i in (peakIdx - 1)..(peakIdx + 1)) {
                sample(ax = 25f, ay = 10f, az = 5f, gz = -100f)
            } else {
                sample(ax = 1f, ay = 0.5f, az = 0.5f, gz = -2f)
            }
        }

        val result = detector.detect(window)
        assertEquals(SwingClassificationKeys.BACKHAND_VOLLEY, result)
    }

    // ── 스트로크(비발리) → null ──────────────────────────────────────────

    @Test
    fun `long impact duration returns null for full stroke`() {
        val window = makeWindow { i ->
            if (i in 5..30) {
                sample(ax = 30f, ay = 10f, az = 5f, gz = 50f)
            } else {
                sample(ax = 1f, ay = 1f, az = 1f, gz = 5f)
            }
        }

        assertNull("긴 임팩트 → 스트로크 → null", detector.detect(window))
    }

    @Test
    fun `high follow-through gyro returns null for topspin stroke`() {
        val peakIdx = 10
        // Follow-through 자이로 크기가 임계값을 넘어야 스트로크로 판정된다.
        // DEFAULT_GYRO_FOLLOW_THROUGH_THRESHOLD_SQ = 1_440_000 (= 1200 dps)이므로
        // 축당 800f → 800²×3 = 1_920_000 > 1_440_000 으로 임계값을 초과한다.
        val window = makeWindow { i ->
            if (i in (peakIdx - 1)..(peakIdx + 1)) {
                sample(ax = 25f, ay = 10f, az = 5f, gz = 50f)
            } else {
                sample(ax = 1f, ay = 1f, az = 1f, gx = 800f, gy = 800f, gz = 800f)
            }
        }

        assertNull("높은 Follow-through 회전량 → 스트로크 → null", detector.detect(window))
    }

    @Test
    fun `weak impact returns null`() {
        val window = makeWindow {
            sample(ax = 2f, ay = 2f, az = 2f, gx = 1f, gy = 1f, gz = 1f)
        }

        assertNull("약한 가속도 → 스윙 아님 → null", detector.detect(window))
    }

    @Test
    fun `raw telemetry is populated when detector returns null`() {
        val peakIdx = 20
        val window = makeWindow { i ->
            if (i == peakIdx) {
                sample(ax = 3f, ay = 2f, az = 1f, gx = 40f, gy = 30f, gz = 20f)
            } else {
                sample(ax = 1f, ay = 1f, az = 1f, gx = 20f, gy = 10f, gz = 5f)
            }
        }

        val result = detector.detect(window)
        assertNull("약한 임팩트는 발리가 아니므로 null", result)
        assertTrue("Peak accel raw는 0보다 커야 함", detector.lastPeakAccelSq > 0f)
        assertTrue("Duration raw는 0보다 커야 함", detector.lastDurationMs > 0L)
        assertTrue("Gyro raw는 0보다 커야 함", detector.lastAvgGyroSq > 0f)
    }

    // ── 인스턴스 격리 ────────────────────────────────────────────────────

    @Test
    fun `separate instances have independent state`() {
        val d1 = VolleyDetector()
        val d2 = VolleyDetector()

        val window = makeWindow { sample(ax = 3f, ay = 2f, az = 1f) }
        d1.detect(window)

        assertEquals("d2의 상태는 초기값이어야 함", 0f, d2.lastPeakAccelSq)
        assertTrue("d1의 상태는 갱신되어야 함", d1.lastPeakAccelSq > 0f)
    }

    // ── 경계 조건 ────────────────────────────────────────────────────────

    @Test
    fun `insufficient window size returns null`() {
        val window = makeWindow(size = 10) {
            sample(ax = 30f, ay = 10f, az = 5f)
        }

        assertNull("윈도우 부족 → null", detector.detect(window))
    }

    @Test
    fun `impact at window boundary still works`() {
        val window = makeWindow { i ->
            if (i >= 37) {
                sample(ax = 25f, ay = 10f, az = 5f, gz = 30f)
            } else {
                sample(ax = 1f, ay = 0.5f, az = 0.5f, gz = 1f)
            }
        }

        val result = detector.detect(window)
        assertNull("임팩트 후 Follow-through 샘플 부족 → null", result)
    }

    @Test
    fun `impact at beginning with enough follow-through detects volley`() {
        val window = makeWindow { i ->
            if (i in 0..2) {
                sample(ax = 25f, ay = 10f, az = 5f, gz = 50f)
            } else {
                sample(ax = 1f, ay = 0.5f, az = 0.5f, gz = 3f)
            }
        }

        val result = detector.detect(window)
        assertNotNull("윈도우 시작부 임팩트 + 충분한 Follow-through → 발리 감지", result)
    }
}
