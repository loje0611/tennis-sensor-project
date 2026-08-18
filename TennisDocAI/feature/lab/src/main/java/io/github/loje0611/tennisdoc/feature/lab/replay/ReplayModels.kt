package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.ui.geometry.Rect
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.vision.analyzer.SwingPathClassifier
import io.github.loje0611.tennisdoc.core.vision.analyzer.SwingPathType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import java.io.File

data class ReplayTooltip(
    val targetJointIndex: Int,
    val jointX: Float,
    val jointY: Float,
    val text: String
)

data class TooltipBoxRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val tooltip: ReplayTooltip
) {
    fun toRect(): Rect = Rect(left, top, right, bottom)
}

/**
 * 스윙 궤적 아크(Motion Trail) 상의 단일 포인트.
 * x, y는 0.0~1.0 정규화 좌표, progress는 시간 진행도(0.0=과거/흐릿, 1.0=임팩트/선명).
 */
data class TrailPoint(
    val x: Float,
    val y: Float,
    val progress: Float
)

data class LabReplayUiState(
    val fusedSwing: FusedSwing? = null,
    val durationMs: Long = 0L,
    val currentTimestampMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val currentPoseFrame: PoseFrame? = null,
    val currentImuPoint: ImuDataPoint? = null,
    val isImpactFrame: Boolean = false,
    val impactTimestampMs: Long = 0L,
    val tooltips: List<ReplayTooltip> = emptyList(),
    // 비디오 + 스윙 궤적 관련 확장 필드
    val videoPath: String? = null,
    val hasVideo: Boolean = false,
    val swingTrailPoints: List<TrailPoint> = emptyList(),
    val swingPathType: String = "",
    val faceStateLabel: String = "",
    val coachingOneLiner: String = ""
)

fun isExistingVideoFile(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    return try {
        File(path).exists()
    } catch (_: Exception) {
        false
    }
}

fun racketFaceStateLabel(state: RacketFaceState): String = when (state) {
    RacketFaceState.SQUARE -> "🟢 정타 (스퀘어)"
    RacketFaceState.OPEN -> "🟠 페이스 열림 (공이 뜨는 원인)"
    RacketFaceState.CLOSED -> "🔵 페이스 닫힘 (네트에 걸리는 원인)"
}

fun swingPathTypeDisplayLabel(type: SwingPathType): String = when (type) {
    SwingPathType.TOPSPIN -> "📈 탑스핀 (상향 스윙 궤적)"
    SwingPathType.FLAT -> "⚡ 플랫 (수평 스윙 궤적)"
    SwingPathType.SLICE -> "📉 슬라이스 (하향 스윙 궤적)"
    SwingPathType.UNKNOWN -> "알 수 없음"
}

fun buildSwingTrailPoints(
    poseFrames: List<PoseFrame>,
    isRightHand: Boolean = true
): List<TrailPoint> {
    val landmarks = SwingPathClassifier.getWristTrajectory3d(poseFrames, isRightHand)
    if (landmarks.isEmpty()) return emptyList()
    if (landmarks.size == 1) {
        return listOf(TrailPoint(x = landmarks[0].x, y = landmarks[0].y, progress = 1f))
    }
    val denom = (landmarks.size - 1).toFloat()
    return landmarks.mapIndexed { index, landmark ->
        TrailPoint(
            x = landmark.x,
            y = landmark.y,
            progress = index.toFloat() / denom
        )
    }
}
