package io.github.loje0611.tennisdoc.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lab_raw_records",
    foreignKeys = [
        ForeignKey(
            entity = SwingSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class LabRawRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val drillType: String,
    val timestampMillis: Long,
    val imuRawJson: String,
    val visionPosesJson: String,
    val impactOffsetMs: Long = 0L
)
