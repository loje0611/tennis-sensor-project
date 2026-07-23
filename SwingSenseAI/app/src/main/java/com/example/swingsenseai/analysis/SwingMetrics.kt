package com.example.swingsenseai.analysis

/**
 * 스윙의 질적 수준을 나타내는 6개 지표 (0~100 스케일).
 */
data class SwingMetrics(
    val power: Int,
    val spin: Int,
    val timing: Int,
    val smoothness: Int,
    val stability: Int,
    val consistency: Int,
) {
    /** 6개 지표 모두 [threshold] 이상인지 확인. */
    fun allAbove(threshold: Int): Boolean =
        power >= threshold && spin >= threshold && timing >= threshold &&
            smoothness >= threshold && stability >= threshold && consistency >= threshold
}
