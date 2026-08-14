package io.github.loje0611.tennisdoc.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Entity(tableName = "swing_sessions")
data class SwingSessionEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    /** UI 표시용, 예: "2026년 4월 12일 오후 2:30" */
    val sessionName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val totalSwingCount: Int = 0,
    val durationMillis: Long = 0L,
    val forehandVolleyCount: Int = 0,
    val backhandVolleyCount: Int = 0,
    val sessionType: String = "MATCH",
    val drillType: String? = null,
) {
    companion object {
        private val SESSION_NAME_FORMAT = SimpleDateFormat("yyyy.MM.dd hh:mm a", Locale.US)

        fun formatSessionName(timeMillis: Long): String =
            SESSION_NAME_FORMAT.format(Date(timeMillis))
    }
}
