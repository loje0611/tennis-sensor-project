package io.github.loje0611.tennisdoc.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "swing_events",
    foreignKeys = [
        ForeignKey(
            entity = SwingSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SwingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** FK → swing_sessions.sessionId */
    val sessionId: String,
    /** 정규화된 영문 구종 키, 예: "forehand topspin" */
    val categoryKey: String,
    val timestampMillis: Long,
    val power: Int,
    val spin: Int,
    val timing: Int,
    val fluidity: Int,
    val stability: Int,
    val consistency: Int,
    /** Peak acceleration magnitude in g-force. */
    val rawMaxAccel: Float = 0f,
    /** Impact duration in milliseconds. */
    val rawDurationMs: Int = 0,
    /** Average gyro follow-through magnitude in dps. */
    val rawGyroFollow: Float = 0f,
)
