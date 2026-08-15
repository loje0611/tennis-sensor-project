package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
    isMirrored: Boolean = false,
    videoAspectRatio: Float = 0.75f, // 3:4 portrait aspect ratio (480x640)
    isFillCenter: Boolean = true
) {
    // Clean Sunlit Court Dual-Stroke Color System
    val upperOuterColor = Color(0xFF0B192C) // Deep Navy
    val upperCoreColor = Color(0xFF00D2FF)  // Electric Sky Blue

    val lowerOuterColor = Color(0xFF0A2E12) // Deep Forest
    val lowerCoreColor = Color(0xFF10B981)  // Vivid Tennis Lime

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
            if (lm.visibility < 0.4f || lm.isNan) return null
            val normalizedX = if (isMirrored) (1f - lm.x) else lm.x
            return Offset(offsetX + normalizedX * displayedWidth, offsetY + lm.y * displayedHeight)
        }

        val outerWidth = 4.5.dp.toPx()
        val coreWidth = 2.5.dp.toPx()

        fun drawDualStrokeBone(startIndex: Int, endIndex: Int, outerColor: Color, coreColor: Color) {
            val start = getPoint(startIndex)
            val end = getPoint(endIndex)
            if (start != null && end != null) {
                // 1. Dark Outline
                drawLine(
                    color = outerColor,
                    start = start,
                    end = end,
                    strokeWidth = outerWidth,
                    cap = StrokeCap.Round
                )
                // 2. Vivid Neon Core
                drawLine(
                    color = coreColor,
                    start = start,
                    end = end,
                    strokeWidth = coreWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        // --- Upper Body Bones (Deep Navy + Electric Sky Blue) ---
        // Shoulders
        drawDualStrokeBone(11, 12, upperOuterColor, upperCoreColor)
        // Right Arm
        drawDualStrokeBone(12, 14, upperOuterColor, upperCoreColor)
        drawDualStrokeBone(14, 16, upperOuterColor, upperCoreColor)
        // Left Arm
        drawDualStrokeBone(11, 13, upperOuterColor, upperCoreColor)
        drawDualStrokeBone(13, 15, upperOuterColor, upperCoreColor)

        // --- Torso & Lower Body Bones (Deep Forest + Vivid Tennis Lime) ---
        // Torso connectors
        drawDualStrokeBone(12, 24, lowerOuterColor, lowerCoreColor)
        drawDualStrokeBone(24, 23, lowerOuterColor, lowerCoreColor)
        drawDualStrokeBone(23, 11, lowerOuterColor, lowerCoreColor)
        // Right Leg
        drawDualStrokeBone(24, 26, lowerOuterColor, lowerCoreColor)
        drawDualStrokeBone(26, 28, lowerOuterColor, lowerCoreColor)
        // Left Leg
        drawDualStrokeBone(23, 25, lowerOuterColor, lowerCoreColor)
        drawDualStrokeBone(25, 27, lowerOuterColor, lowerCoreColor)

        // --- Landmark Joint Points ---
        val keyJointIndices = setOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)

        for (i in landmarks.indices) {
            val pt = getPoint(i) ?: continue
            val isUpper = i in 11..16
            val outerCol = if (isUpper) upperOuterColor else lowerOuterColor
            val coreCol = if (isUpper) upperCoreColor else lowerCoreColor

            if (i in keyJointIndices) {
                // Key joints: 2-layer ring
                drawCircle(color = outerCol, radius = 5.dp.toPx(), center = pt)
                drawCircle(color = coreCol, radius = 3.2.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 1.2.dp.toPx(), center = pt)
            } else {
                drawCircle(color = outerCol, radius = 3.5.dp.toPx(), center = pt)
                drawCircle(color = coreCol, radius = 2.dp.toPx(), center = pt)
            }
        }
    }
}
