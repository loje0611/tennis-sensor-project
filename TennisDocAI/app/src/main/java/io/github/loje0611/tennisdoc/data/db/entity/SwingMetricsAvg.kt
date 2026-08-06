package io.github.loje0611.tennisdoc.data.db.entity

/**
 * DAO AVG 쿼리 결과 매핑용 POJO.
 * Room @Query가 이 클래스로 직접 투영할 수 있도록 컬럼명과 일치한다.
 */
data class SwingMetricsAvg(
    val power: Double,
    val spin: Double,
    val timing: Double,
    val fluidity: Double,
    val stability: Double,
    val consistency: Double,
)
