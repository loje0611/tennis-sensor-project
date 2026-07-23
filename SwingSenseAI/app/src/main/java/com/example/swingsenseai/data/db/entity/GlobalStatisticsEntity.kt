package com.example.swingsenseai.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 구종(categoryKey)별 누적 평균 지표.
 * 매 스윙마다 온라인 평균(running average)으로 갱신한다.
 * `NewAvg = OldAvg + (NewValue - OldAvg) / NewCount`
 */
@Entity(tableName = "global_statistics")
data class GlobalStatisticsEntity(
    @PrimaryKey val categoryKey: String,
    val count: Long = 0,
    val avgPower: Double = 0.0,
    val avgSpin: Double = 0.0,
    val avgTiming: Double = 0.0,
    val avgFluidity: Double = 0.0,
    val avgStability: Double = 0.0,
    val avgConsistency: Double = 0.0,
)
