package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

@Composable
fun PoseOverlayCanvas(
    poseFrame: PoseFrame?,
    modifier: Modifier = Modifier,
    videoAspectRatio: Float = 0.75f, // 3:4 portrait aspect ratio (480x640)
    isFillCenter: Boolean = true
) {
    val pointColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier.fillMaxSize()) {
        if (poseFrame == null || poseFrame.landmarks.isEmpty()) return@Canvas

        val viewWidth = size.width
        val viewHeight = size.height
        if (viewWidth <= 0f || viewHeight <= 0f) return@Canvas

        val viewAspect = viewWidth / viewHeight

        val displayedWidth: Float
        val displayedHeight: Float
        val offsetX: Float
        val offsetY: Float

        if (isFillCenter) {
            if (viewAspect < videoAspectRatio) {
                // View is taller than video (crop left & right)
                displayedHeight = viewHeight
                displayedWidth = viewHeight * videoAspectRatio
                offsetX = (viewWidth - displayedWidth) / 2f
                offsetY = 0f
            } else {
                // View is wider than video (crop top & bottom)
                displayedWidth = viewWidth
                displayedHeight = viewWidth / videoAspectRatio
                offsetX = 0f
                offsetY = (viewHeight - displayedHeight) / 2f
            }
        } else { // FIT_CENTER
            if (viewAspect < videoAspectRatio) {
                displayedWidth = viewWidth
                displayedHeight = viewWidth / videoAspectRatio
                offsetX = 0f
                offsetY = (viewHeight - displayedHeight) / 2f
            } else {
                displayedHeight = viewHeight
                displayedWidth = viewHeight * videoAspectRatio
                offsetX = (viewWidth - displayedWidth) / 2f
                offsetY = 0f
            }
        }

        val landmarks = poseFrame.landmarks

        fun getPoint(index: Int): Offset? {
            if (index !in landmarks.indices) return null
            val lm = landmarks[index]
            if (lm.visibility < 0.5f || lm.isNan) return null
            return Offset(offsetX + lm.x * displayedWidth, offsetY + lm.y * displayedHeight)
        }

        fun drawBone(startIndex: Int, endIndex: Int) {
            val start = getPoint(startIndex)
            val end = getPoint(endIndex)
            if (start != null && end != null) {
                drawLine(
                    color = lineColor,
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Connections
        // Torso: 11-12, 12-24, 24-23, 23-11
        drawBone(11, 12)
        drawBone(12, 24)
        drawBone(24, 23)
        drawBone(23, 11)

        // Right Arm: 12-14, 14-16
        drawBone(12, 14)
        drawBone(14, 16)

        // Left Arm: 11-13, 13-15
        drawBone(11, 13)
        drawBone(13, 15)

        // Right Leg: 24-26, 26-28
        drawBone(24, 26)
        drawBone(26, 28)

        // Left Leg: 23-25, 25-27
        drawBone(23, 25)
        drawBone(25, 27)

        // Points
        for (i in landmarks.indices) {
            val pt = getPoint(i)
            if (pt != null) {
                drawCircle(
                    color = pointColor,
                    radius = 4.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
