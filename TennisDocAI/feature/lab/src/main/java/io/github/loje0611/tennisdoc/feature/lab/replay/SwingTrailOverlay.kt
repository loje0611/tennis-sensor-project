package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val TrailNeonGreen = Color(0xFF00E676)
private val TrailGlow = Color(0xFF69F0AE)

@Composable
fun SwingTrailOverlay(
    swingTrailPoints: List<TrailPoint>,
    isImpact: Boolean,
    canvasSize: Size,
    modifier: Modifier = Modifier
) {
    if (swingTrailPoints.isEmpty()) return

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = if (canvasSize.width > 0f) canvasSize.width else size.width
            val height = if (canvasSize.height > 0f) canvasSize.height else size.height
            if (width <= 0f || height <= 0f || swingTrailPoints.size < 2) return@Canvas

            val minStroke = 2.dp.toPx()
            val maxStroke = 6.dp.toPx()
            val impactStart = (swingTrailPoints.lastIndex - 5).coerceAtLeast(0)

            val trailPath = Path()
            val first = swingTrailPoints.first()
            trailPath.moveTo(first.x * width, first.y * height)
            for (i in 1 until swingTrailPoints.size) {
                val point = swingTrailPoints[i]
                trailPath.lineTo(point.x * width, point.y * height)
            }

            for (i in 0 until swingTrailPoints.lastIndex) {
                val start = swingTrailPoints[i]
                val end = swingTrailPoints[i + 1]
                val progress = (start.progress + end.progress) / 2f
                val strokeWidth = minStroke + (maxStroke - minStroke) * progress
                val alpha = 0.2f + 0.8f * progress
                val from = Offset(start.x * width, start.y * height)
                val to = Offset(end.x * width, end.y * height)

                if (i >= impactStart) {
                    drawLine(
                        color = TrailGlow.copy(alpha = 0.6f),
                        start = from,
                        end = to,
                        strokeWidth = strokeWidth * 2.4f,
                        cap = StrokeCap.Round,
                        blendMode = BlendMode.Screen
                    )
                }

                drawLine(
                    color = TrailNeonGreen.copy(alpha = alpha),
                    start = from,
                    end = to,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            drawPath(
                path = trailPath,
                color = Color.Transparent,
                style = Stroke(
                    width = 1f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        if (isImpact) {
            val impactPoint = swingTrailPoints.maxBy { it.progress }
            val width = canvasSize.width
            val height = canvasSize.height
            Surface(
                color = Color(0xFFFF3D00),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .offset {
                        val x = (impactPoint.x * width).roundToInt()
                        val y = (impactPoint.y * height).roundToInt()
                        IntOffset(x.coerceAtLeast(0), (y - 36).coerceAtLeast(0))
                    }
                    .padding(4.dp)
            ) {
                Text(
                    text = "IMPACT!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
