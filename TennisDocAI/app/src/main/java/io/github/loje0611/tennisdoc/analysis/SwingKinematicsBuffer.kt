package io.github.loje0611.tennisdoc.analysis

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 스윙 운동학 분석 전용 2000ms 원형 버퍼 (50Hz × 100샘플).
 *
 * 기존 분류용 [SwingInferenceBuffer](800ms)와 **완전히 독립**으로 운영된다.
 * 쿨다운과 무관하게 항상 최신 100샘플을 유지하며,
 * 분류가 성공하면 [snapshot]으로 복사본을 꺼내 [KinematicAnalyzer]에 전달한다.
 */
class SwingKinematicsBuffer {

    private val mutex = Mutex()
    private val ring = ArrayDeque<FloatArray>(CAPACITY + 1)

    /**
     * 새 샘플(6축 float)을 버퍼에 추가. 최대 [CAPACITY]를 초과하면 가장 오래된 것을 버린다.
     * 분류 버퍼 쿨다운과 무관하게 **항상** 호출되어야 한다.
     */
    suspend fun addSample(sixAxes: FloatArray) {
        mutex.withLock {
            if (ring.size >= CAPACITY) {
                ring.removeFirst()
            }
            ring.addLast(sixAxes.copyOf())
        }
    }

    /**
     * 현재 버퍼 전체의 **방어적 복사본**을 반환한다.
     * 반환된 리스트의 각 FloatArray는 원본과 독립이므로 안전하게 사용 가능.
     */
    suspend fun snapshot(): List<FloatArray> {
        return mutex.withLock {
            ring.map { it.copyOf() }
        }
    }

    /** 버퍼 초기화 (세션 종료/재시작 시 사용). */
    suspend fun reset() {
        mutex.withLock {
            ring.clear()
        }
    }

    companion object {
        /** 50Hz × 2000ms = 100 샘플. */
        const val CAPACITY = 100
    }
}
