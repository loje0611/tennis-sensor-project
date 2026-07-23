package com.example.swingsenseai.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "session_swing_counts",
    primaryKeys = ["sessionId", "categoryKey"],
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
data class SessionSwingCountEntity(
    val sessionId: String,
    /** 영문 구종 키, 예: "forehand topspin" */
    val categoryKey: String,
    val count: Int,
)
