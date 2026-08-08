package io.github.loje0611.tennisdoc.feature.history

import android.graphics.BlurMaskFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.core.model.SwingMetrics
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val AXIS_LABELS_KO = listOf("파워", "스핀", "타이밍", "부드러움", "안정성", "일관성")

private val TOOLTIP_TEXTS = mapOf(
    "파워" to "라켓이 공에 전달한 최대 충격량입니다. (가속도 기반)",
    "스핀" to "임팩트 직후 라켓이 만들어낸 회전량입니다. (자이로스코프 기반)",
    "타이밍" to "단순히 공을 맞춘 순간이 아니라, '내 스윙의 힘이 최고조에 달한 최적의 타점에서 임팩트가 이루어졌는가'를 의미합니다. 스윙 궤적의 가속 타이밍과 임팩트 순간이 완벽하게 맞아떨어질수록 점수가 높아집니다.",
    "부드러움" to "테이크백부터 팔로스루까지, '가속이 끊기거나 멈칫거림 없이 얼마나 매끄럽게 이어지는가'를 보여줍니다. 힘을 억지로 쥐어짜는 로봇 같은 스윙이 아닌, 키네틱 체인(Kinetic Chain)을 활용해 채찍처럼 유려하게 휘두를수록 높은 점수를 받습니다.",
    "안정성" to "무거운 공과 라켓이 충돌하는 찰나의 순간, '내 손목과 라켓 면이 충격에 밀리지 않고 얼마나 견고하게 버텼는가'를 의미합니다. 임팩트 직후 라켓의 미세한 떨림이나 궤적의 틀어짐이 적을수록 점수가 올라갑니다.",
    "일관성" to "테니스는 결국 반복의 예술입니다. '오늘 내가 친 이전 스윙들의 궤적, 템포, 힘과 현재 스윙이 얼마나 오차 없이 일치하는가'를 측정합니다. 체력이 떨어져 폼이 무너지면 점수가 하락하며, 기계처럼 똑같은 스윙을 반복할수록 100점에 가까워집니다.",
)

@Stable
private data class LabelHitTarget(
    val label: String,
    val center: Offset,
)

/**
 * 6각형 듀얼-레이어 레이더 차트.
 * - 각 꼭짓점: **"라벨 점수"** (예: 파워 85).
 * - 12시 방향 반경에 **0 / 50 / 100** 스케일 힌트(흐릿한 회색).
 * - 라벨 터치 시 해당 지표의 코칭 툴팁 표시.
 */
@Composable
fun HexagonalRadarChart(
    metrics: SwingMetrics,
    historyMetrics: SwingMetrics? = null,
    modifier: Modifier = Modifier,
) {
    val neonPurple = SwingTheme.colors.neonPurpleSettings
    val neonPurpleFill = neonPurple.copy(alpha = 0.22f)
    val neonPurpleGlow = neonPurple.copy(alpha = 0.55f)

    val historyGray = SwingTheme.colors.subGray
    val historyGrayFill = historyGray.copy(alpha = 0.10f)
    val gridColor = SwingTheme.colors.cardBorder
    val gridAxisColor = SwingTheme.colors.cardBorder.copy(alpha = 0.6f)
    val scaleTextColor = SwingTheme.colors.subGray
    val axisLabelColor = SwingTheme.colors.onBackground
    val tooltipSurface = SwingTheme.colors.cardSurface
    val tooltipBorder = SwingTheme.colors.cardBorder

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(metrics, historyMetrics) {
        isVisible = false
        kotlinx.coroutines.delay(50)
        isVisible = true
    }

    val animProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "radar_anim",
    )

    val currentValues = listOf(
        metrics.power / 100f,
        metrics.spin / 100f,
        metrics.timing / 100f,
        metrics.smoothness / 100f,
        metrics.stability / 100f,
        metrics.consistency / 100f,
    )

    val scoreInts = listOf(
        metrics.power, metrics.spin, metrics.timing,
        metrics.smoothness, metrics.stability, metrics.consistency,
    )

    val historyValues = historyMetrics?.let {
        listOf(
            it.power / 100f, it.spin / 100f, it.timing / 100f,
            it.smoothness / 100f, it.stability / 100f, it.consistency / 100f,
        )
    }

    val accessibilityDesc = remember(metrics) {
        AXIS_LABELS_KO.zip(scoreInts).joinToString(", ") { (label, score) -> "$label $score" }
    }

    var selectedTooltip by remember { mutableStateOf<String?>(null) }
    var dismissKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedTooltip, dismissKey) {
        if (selectedTooltip != null) {
            kotlinx.coroutines.delay(4000)
            selectedTooltip = null
        }
    }

    val hitTargets = remember { mutableStateOf(emptyList<LabelHitTarget>()) }
    val density = LocalDensity.current
    val hitRadiusPx = with(density) { 40.dp.toPx() }

    val currentSelectedTooltip by rememberUpdatedState(selectedTooltip)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(1f)
            .semantics { contentDescription = "레이더 차트: $accessibilityDesc" },
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { intSize ->
                    val cx = intSize.width / 2f
                    val cy = intSize.height / 2f
                    val radius = minOf(intSize.width, intSize.height) / 2f * 0.68f
                    val labelRadius = radius + 38f
                    hitTargets.value = AXIS_LABELS_KO.mapIndexed { i, label ->
                        val angle = angleForIndex(i)
                        LabelHitTarget(
                            label = label,
                            center = Offset(
                                cx + labelRadius * cos(angle),
                                cy + labelRadius * sin(angle),
                            ),
                        )
                    }
                }
                .pointerInput(hitRadiusPx) {
                    detectTapGestures { tapOffset ->
                        val targets = hitTargets.value
                        val hit = targets.firstOrNull { target ->
                            val dx = tapOffset.x - target.center.x
                            val dy = tapOffset.y - target.center.y
                            sqrt(dx * dx + dy * dy) <= hitRadiusPx
                        }
                        if (hit != null) {
                            if (currentSelectedTooltip == hit.label) {
                                dismissKey++
                            } else {
                                selectedTooltip = hit.label
                            }
                        } else {
                            selectedTooltip = null
                        }
                    }
                },
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension / 2f * 0.68f

            for (level in 1..4) {
                val r = radius * level / 4f
                drawHexagonOutline(cx, cy, r, gridColor, strokeWidth = 1.2f)
            }

            drawRadialScaleHints(cx, cy, radius, scaleTextColor)

            for (i in 0 until 6) {
                val angle = angleForIndex(i)
                val end = Offset(cx + radius * cos(angle), cy + radius * sin(angle))
                drawLine(gridAxisColor, Offset(cx, cy), end, strokeWidth = 1f)
            }

            if (historyValues != null) {
                val historyPath = buildDataPath(cx, cy, radius, historyValues, animProgress)
                drawPath(historyPath, historyGrayFill)
                drawPath(historyPath, historyGray, style = Stroke(width = 2f))
                for (i in 0 until 6) {
                    val v = historyValues[i] * animProgress
                    val angle = angleForIndex(i)
                    val point = Offset(cx + radius * v * cos(angle), cy + radius * v * sin(angle))
                    drawCircle(historyGray.copy(alpha = 0.5f), radius = 3.5f, center = point)
                }
            }

            val dataPath = buildDataPath(cx, cy, radius, currentValues, animProgress)

            drawIntoCanvas { canvas ->
                val glowPaint = Paint().apply {
                    color = neonPurpleGlow
                    asFrameworkPaint().apply {
                        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 6f
                    }
                }
                canvas.drawPath(dataPath, glowPaint)
            }

            drawPath(dataPath, neonPurpleFill)
            drawPath(dataPath, neonPurple, style = Stroke(width = 2.5f))

            for (i in 0 until 6) {
                val v = currentValues[i] * animProgress
                val angle = angleForIndex(i)
                val point = Offset(cx + radius * v * cos(angle), cy + radius * v * sin(angle))
                drawCircle(neonPurpleGlow, radius = 6f, center = point)
                drawCircle(neonPurple, radius = 3.5f, center = point)
            }

            drawAxisLabelsWithScores(
                cx, cy, radius, AXIS_LABELS_KO, scoreInts, axisLabelColor,
            )
        }

        // ── Tooltip Overlay ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = selectedTooltip != null,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(tween(150)) + scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(150),
            ),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val label = selectedTooltip
            if (label != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tooltipSurface.copy(alpha = 0.94f),
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = label,
                            color = neonPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = TOOLTIP_TEXTS[label].orEmpty(),
                            color = axisLabelColor.copy(alpha = 0.88f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Geometry Helpers ──────────────────────────────────────────────────────

private fun angleForIndex(i: Int): Float {
    return (-PI / 2.0 + 2.0 * PI * i / 6.0).toFloat()
}

private fun DrawScope.drawHexagonOutline(cx: Float, cy: Float, r: Float, color: Color, strokeWidth: Float) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = angleForIndex(i)
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

private fun buildDataPath(cx: Float, cy: Float, radius: Float, values: List<Float>, anim: Float): Path {
    val path = Path()
    for (i in 0 until 6) {
        val v = (values[i] * anim).coerceIn(0f, 1f)
        val angle = angleForIndex(i)
        val x = cx + radius * v * cos(angle)
        val y = cy + radius * v * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.drawRadialScaleHints(cx: Float, cy: Float, radius: Float, scaleColor: Color) {
    val angle = angleForIndex(0)
    val cosA = cos(angle)
    val sinA = sin(angle)
    val scalePaint = android.graphics.Paint().apply {
        color = scaleColor.copy(alpha = 0.72f).toArgb()
        textSize = 19f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        textAlign = android.graphics.Paint.Align.CENTER
    }

    drawIntoCanvas { canvas ->
        fun labelAt(t: Float, text: String, dyBias: Float) {
            val px = cx + radius * t * cosA
            val py = cy + radius * t * sinA + dyBias
            canvas.nativeCanvas.drawText(text, px, py, scalePaint)
        }
        labelAt(0.08f, "0", 12f)
        labelAt(0.5f, "50", 6f)
        labelAt(0.96f, "100", 8f)
    }
}

/**
 * 라벨+점수를 그린다. 터치 히트 테스트용 좌표는 onSizeChanged에서 별도 계산.
 */
private fun DrawScope.drawAxisLabelsWithScores(
    cx: Float,
    cy: Float,
    radius: Float,
    labels: List<String>,
    scores: List<Int>,
    labelColor: Color,
) {
    val labelRadius = radius + 38f
    val labelPaint = android.graphics.Paint().apply {
        color = labelColor.toArgb()
        textSize = 22f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        textAlign = android.graphics.Paint.Align.CENTER
    }

    drawIntoCanvas { canvas ->
        for (i in labels.indices) {
            val angle = angleForIndex(i)
            val x = cx + labelRadius * cos(angle)
            val y = cy + labelRadius * sin(angle)
            val yOffset = when {
                angle < -PI / 4 && angle > -3 * PI / 4 -> -12f
                angle > PI / 4 && angle < 3 * PI / 4 -> 10f
                else -> 4f
            }
            val score = scores.getOrElse(i) { 0 }
            canvas.nativeCanvas.drawText("${labels[i]} $score", x, y + yOffset, labelPaint)
        }
    }
}
