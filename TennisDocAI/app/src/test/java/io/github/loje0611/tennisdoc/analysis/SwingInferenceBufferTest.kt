package io.github.loje0611.tennisdoc.analysis

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SwingInferenceBufferTest {

    private fun zeroes() = FloatArray(EdgeImpulseInputSpec.AXES_PER_SAMPLE)

    private fun volleyDetector() = VolleyDetector()

    private fun noOpClassifier(): (FloatArray) -> String = { "" }

    @Test
    fun `onSample returns null until window is full`() = runTest {
        val buffer = SwingInferenceBuffer(volleyDetector(), noOpClassifier())
        for (i in 0 until EdgeImpulseInputSpec.WINDOW_SAMPLES - 1) {
            assertNull(buffer.onSample(zeroes()))
        }
    }

    @Test
    fun `reset clears the window so it needs refill`() = runTest {
        val buffer = SwingInferenceBuffer(volleyDetector(), noOpClassifier())
        repeat(EdgeImpulseInputSpec.WINDOW_SAMPLES - 1) {
            buffer.onSample(zeroes())
        }
        buffer.reset()
        for (i in 0 until EdgeImpulseInputSpec.WINDOW_SAMPLES - 1) {
            assertNull(buffer.onSample(zeroes()))
        }
    }

    @Test
    fun `debug cooldown blocks samples`() = runTest {
        val buffer = SwingInferenceBuffer(volleyDetector(), noOpClassifier())
        buffer.applyDebugSimulatedCooldown()
        assertNull(buffer.onSample(zeroes()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `onSample rejects wrong size`() = runTest {
        val buffer = SwingInferenceBuffer(volleyDetector(), noOpClassifier())
        buffer.onSample(FloatArray(3))
    }

    // ── Happy Path: 발리 감지 ────────────────────────────────────────────

    @Test
    fun `full window with volley pattern returns volley label`() = runTest {
        val detector = VolleyDetector()
        val buffer = SwingInferenceBuffer(detector, noOpClassifier())

        val peakIdx = 20
        for (i in 0 until EdgeImpulseInputSpec.WINDOW_SAMPLES) {
            val sample = if (i in (peakIdx - 1)..(peakIdx + 1)) {
                floatArrayOf(25f, 10f, 5f, 5f, 5f, 100f)
            } else {
                floatArrayOf(1f, 0.5f, 0.5f, 3f, 3f, 2f)
            }

            val result = buffer.onSample(sample)

            if (i < EdgeImpulseInputSpec.WINDOW_SAMPLES - 1) {
                assertNull("윈도우 미충족 시 null", result)
            } else {
                assertNotNull("윈도우 충족 + 발리 패턴 → 라벨 반환", result)
                assertEquals(SwingClassificationKeys.FOREHAND_VOLLEY, result)
            }
        }
    }

    // ── Happy Path: AI 분류기 호출 ───────────────────────────────────────

    @Test
    fun `full window with non-volley data delegates to classifier`() = runTest {
        val mockLabel = "forehand topspin"
        val classifierCalled = mutableListOf<Boolean>()
        val classifier: (FloatArray) -> String = {
            classifierCalled.add(true)
            mockLabel
        }

        val buffer = SwingInferenceBuffer(volleyDetector(), classifier)

        for (i in 0 until EdgeImpulseInputSpec.WINDOW_SAMPLES) {
            val sample = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f)
            val result = buffer.onSample(sample)

            if (i < EdgeImpulseInputSpec.WINDOW_SAMPLES - 1) {
                assertNull(result)
            } else {
                assertEquals(mockLabel, result)
                assertTrue("AI 분류기가 호출되어야 함", classifierCalled.isNotEmpty())
            }
        }
    }

    // ── 쿨다운 후 재충전 ─────────────────────────────────────────────────

    @Test
    fun `after detection cooldown blocks and then allows new window`() = runTest {
        val mockLabel = "backhand slice"
        val classifier: (FloatArray) -> String = { mockLabel }
        val buffer = SwingInferenceBuffer(volleyDetector(), classifier)

        repeat(EdgeImpulseInputSpec.WINDOW_SAMPLES) {
            buffer.onSample(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f))
        }

        val immediateAfter = buffer.onSample(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f))
        assertNull("쿨다운 중에는 null 반환", immediateAfter)
    }
}
