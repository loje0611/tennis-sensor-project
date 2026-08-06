package io.github.loje0611.tennisdoc.analysis

import io.github.loje0611.tennisdoc.inference.EdgeImpulseNative
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 50Hz IMU 슬라이딩 윈도(최신 [EdgeImpulseInputSpec.WINDOW_SAMPLES]샘플 ≈ 800ms) + Two-Stage 추론 + 1.5초 쿨다운.
 *
 * **Stage 1**: 윈도가 가득 차면 먼저 [VolleyDetector.detect]로 물리 기반 발리 판별을 수행한다.
 * 발리로 확정되면 JNI 호출 없이 즉시 라벨을 반환한다.
 *
 * **Stage 2**: Stage 1에서 `null`(스트로크)이면 [classifier]를 호출한다.
 *
 * 쿨다운 중에는 샘플을 버퍼에 넣지 않는다.
 *
 * 성능 최적화: scratch FloatArray를 재사용하여 GC 압력을 최소화한다.
 *
 * @param volleyDetector Stage 1 발리 판별기 인스턴스
 * @param classifier Stage 2 AI 분류 함수 (테스트 시 mock 가능)
 */
class SwingInferenceBuffer(
    private val volleyDetector: VolleyDetector,
    private val classifier: (FloatArray) -> String = EdgeImpulseNative::runClassifier,
) {

    private val mutex = Mutex()
    private val window = ArrayDeque<FloatArray>(EdgeImpulseInputSpec.WINDOW_SAMPLES)
    private var cooldownUntilNanos: Long = 0L

    private val flatScratch = FloatArray(EdgeImpulseInputSpec.FLAT_SIZE)

    suspend fun reset() {
        mutex.withLock {
            window.clear()
            cooldownUntilNanos = 0L
        }
    }

    suspend fun applyDebugSimulatedCooldown() {
        mutex.withLock {
            cooldownUntilNanos = System.nanoTime() +
                TimeUnit.MILLISECONDS.toNanos(COOLDOWN_MS)
            window.clear()
        }
    }

    /**
     * @return 추론에 성공해 갱신된 영문 라벨, 그 외 null (UI는 이전 값 유지)
     */
    suspend fun onSample(sixAxes: FloatArray): String? {
        require(sixAxes.size == EdgeImpulseInputSpec.AXES_PER_SAMPLE) {
            "expected ${EdgeImpulseInputSpec.AXES_PER_SAMPLE} floats"
        }

        var windowReady = false

        mutex.withLock {
            val now = System.nanoTime()
            if (now < cooldownUntilNanos) {
                return null
            }
            if (window.size == EdgeImpulseInputSpec.WINDOW_SAMPLES) {
                window.removeFirst()
            }
            window.addLast(sixAxes.copyOf())
            if (window.size < EdgeImpulseInputSpec.WINDOW_SAMPLES) {
                return null
            }
            windowReady = true
            flattenIntoScratch()
        }

        if (!windowReady) return null

        // ── Stage 1: 물리 기반 발리 게이트키퍼 ──
        val volleyLabel = volleyDetector.detectFromFlat(
            flatScratch,
            EdgeImpulseInputSpec.WINDOW_SAMPLES,
            EdgeImpulseInputSpec.AXES_PER_SAMPLE,
        )
        if (volleyLabel != null) {
            mutex.withLock {
                cooldownUntilNanos = System.nanoTime() +
                    TimeUnit.MILLISECONDS.toNanos(COOLDOWN_MS)
                window.clear()
            }
            return volleyLabel
        }

        // ── Stage 2: AI 모델 추론 ──
        val label = classifier(flatScratch)

        mutex.withLock {
            if (label.isNotEmpty()) {
                cooldownUntilNanos = System.nanoTime() +
                    TimeUnit.MILLISECONDS.toNanos(COOLDOWN_MS)
                window.clear()
            } else {
                if (window.isNotEmpty()) {
                    window.removeFirst()
                }
            }
        }

        return label.takeIf { it.isNotEmpty() }
    }

    private fun flattenIntoScratch() {
        var offset = 0
        for (row in window) {
            row.copyInto(flatScratch, offset)
            offset += EdgeImpulseInputSpec.AXES_PER_SAMPLE
        }
    }

    companion object {
        private const val COOLDOWN_MS = 1500L
    }
}
