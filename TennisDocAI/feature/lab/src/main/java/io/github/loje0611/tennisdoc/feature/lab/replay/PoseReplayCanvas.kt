package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

private val POSE_CONNECTIONS = listOf(
    Pair(11, 12), // 어깨
    Pair(11, 13), Pair(13, 15), // 왼팔
    Pair(12, 14), Pair(14, 16), // 오른팔
    Pair(11, 23), Pair(12, 24), // 몸통
    Pair(23, 24), // 골반
    Pair(23, 25), Pair(25, 27), Pair(27, 29), Pair(27, 31), // 왼다리/발
    Pair(24, 26), Pair(26, 28), Pair(28, 30), Pair(28, 32)  // 오른다리/발
)

@Composable
fun PoseReplayCanvas(
    poseFrame: PoseFrame?,
    isImpact: Boolean,
    tooltips: List<ReplayTooltip>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .background(Color(0xFF121212), RoundedCornerShape(12.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (poseFrame != null && poseFrame.landmarks.isNotEmpty()) {
                val landmarks = poseFrame.landmarks

                // 1. 뼈대 연결선 렌더링
                for ((startIndex, endIndex) in POSE_CONNECTIONS) {
                    val start = landmarks.getOrNull(startIndex)
                    val end = landmarks.getOrNull(endIndex)
                    if (start != null && end != null && start.visibility > 0.3f && end.visibility > 0.3f) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = Offset(start.x * canvasWidth, start.y * canvasHeight),
                            end = Offset(end.x * canvasWidth, end.y * canvasHeight),
                            strokeWidth = 3f
                        )
                    }
                }

                // 2. 관절 키포인트 렌더링
                for (joint in landmarks) {
                    if (joint.visibility > 0.3f) {
                        drawCircle(
                            color = Color(0xFF00E676),
                            radius = 6f,
                            center = Offset(joint.x * canvasWidth, joint.y * canvasHeight)
                        )
                    }
                }

                // 3. 툴팁 렌더링 (불변식 준수 배치)
                if (tooltips.isNotEmpty()) {
                    val textStyle = TextStyle(
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val boxSizes = tooltips.map { tooltip ->
                        val textLayout = textMeasurer.measure(tooltip.text, textStyle)
                        Size(textLayout.size.width.toFloat() + 20f, textLayout.size.height.toFloat() + 14f)
                    }

                    val placedRects = TooltipPlacementCalculator.computePlacement(
                        tooltips = tooltips,
                        boxSizes = boxSizes,
                        canvasSize = size
                    )

                    for (idx in placedRects.indices) {
                        val rect = placedRects[idx]
                        val tooltip = tooltips[idx]
                        val jointX = tooltip.jointX * canvasWidth
                        val jointY = tooltip.jointY * canvasHeight

                        // 대상 관절 마커 (이중 원)
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = 10f,
                            center = Offset(jointX, jointY),
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = 5f,
                            center = Offset(jointX, jointY)
                        )

                        // 지시선 (관절 중심 -> 박스 중심)
                        val boxCenterX = (rect.left + rect.right) / 2f
                        val boxCenterY = (rect.top + rect.bottom) / 2f
                        drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                            start = Offset(jointX, jointY),
                            end = Offset(boxCenterX, boxCenterY),
                            strokeWidth = 2f
                        )

                        // 텍스트 박스 배경 & 테두리
                        drawRect(
                            color = Color.Black.copy(alpha = 0.85f),
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width, rect.height)
                        )
                        drawRect(
                            color = Color(0xFF00E5FF),
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width, rect.height),
                            style = Stroke(width = 2f)
                        )

                        // 텍스트 렌더링
                        drawText(
                            textMeasurer = textMeasurer,
                            text = tooltip.text,
                            style = textStyle,
                            topLeft = Offset(rect.left + 10f, rect.top + 7f)
                        )
                    }
                }
            } else {
                // 프레임 없음 표시
                drawText(
                    textMeasurer = textMeasurer,
                    text = "포즈 데이터 대기 중...",
                    style = TextStyle(color = Color.Gray, fontSize = 14.sp),
                    topLeft = Offset(canvasWidth / 2f - 60f, canvasHeight / 2f - 10f)
                )
            }
        }

        // 임팩트 뱃지 (상단 중앙/좌상단)
        if (isImpact) {
            Surface(
                color = Color(0xFFFF3D00),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "⚡ IMPACT!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
