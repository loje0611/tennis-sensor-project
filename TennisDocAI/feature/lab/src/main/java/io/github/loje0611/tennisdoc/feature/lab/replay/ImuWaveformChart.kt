package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import kotlin.math.sqrt

@Composable
fun ImuWaveformChart(
    imuSamples: List<ImuDataPoint>,
    kineticChain: KineticChain5Stage?,
    currentTimestampMs: Long,
    timeOffsetMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1E1E24),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 범례 및 라벨
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IMU 동기 파형 & 운동체인 피크",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E5FF), CircleShape))
                    Text(text = " 가속도(|a|)", color = Color.LightGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.size(8.dp))
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFD600), CircleShape))
                    Text(text = " 각속도(|ω|)", color = Color.LightGray, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 파형 캔버스
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    if (imuSamples.size >= 2 && durationMs > 0L) {
                        val baseTime = imuSamples.first().timestampMs
                        val totalTime = durationMs.toFloat()

                        // 최대값 계산 (정규화용)
                        var maxAccel = 1f
                        var maxGyro = 1f
                        for (sample in imuSamples) {
                            val accelMag = sqrt(sample.accelX * sample.accelX + sample.accelY * sample.accelY + sample.accelZ * sample.accelZ)
                            val gyroMag = sqrt(sample.gyroX * sample.gyroX + sample.gyroY * sample.gyroY + sample.gyroZ * sample.gyroZ)
                            if (accelMag > maxAccel) maxAccel = accelMag
                            if (gyroMag > maxGyro) maxGyro = gyroMag
                        }

                        val accelPath = Path()
                        val gyroPath = Path()

                        var isFirst = true
                        for (sample in imuSamples) {
                            val relTime = (sample.timestampMs - baseTime).coerceAtLeast(0L).toFloat()
                            val x = (relTime / totalTime) * w

                            val accelMag = sqrt(sample.accelX * sample.accelX + sample.accelY * sample.accelY + sample.accelZ * sample.accelZ)
                            val accelY = h - (accelMag / maxAccel) * (h * 0.85f) - (h * 0.05f)

                            val gyroMag = sqrt(sample.gyroX * sample.gyroX + sample.gyroY * sample.gyroY + sample.gyroZ * sample.gyroZ)
                            val gyroY = h - (gyroMag / maxGyro) * (h * 0.85f) - (h * 0.05f)

                            if (isFirst) {
                                accelPath.moveTo(x, accelY)
                                gyroPath.moveTo(x, gyroY)
                                isFirst = false
                            } else {
                                accelPath.lineTo(x, accelY)
                                gyroPath.lineTo(x, gyroY)
                            }
                        }

                        // 가속도 곡선
                        drawPath(
                            path = accelPath,
                            color = Color(0xFF00E5FF),
                            style = Stroke(width = 2f)
                        )

                        // 각속도 곡선
                        drawPath(
                            path = gyroPath,
                            color = Color(0xFFFFD600),
                            style = Stroke(width = 2f)
                        )

                        // 5단계 운동 체인 피크 마커 표시
                        if (kineticChain != null) {
                            for (stage in kineticChain.stages) {
                                val color = when (stage.stage) {
                                    KineticStageType.HIP -> Color(0xFF4CAF50)
                                    KineticStageType.SHOULDER -> Color(0xFF2196F3)
                                    KineticStageType.WRIST -> Color(0xFFFF9800)
                                    KineticStageType.RACKET -> Color(0xFFE91E63)
                                    KineticStageType.IMPACT -> Color(0xFFFF1744)
                                }
                                val relPeak = (stage.peakTimestampMs - baseTime).toFloat()
                                if (relPeak in 0f..totalTime) {
                                    val px = (relPeak / totalTime) * w
                                    drawCircle(
                                        color = color,
                                        radius = 5f,
                                        center = Offset(px, h * 0.2f)
                                    )
                                    drawLine(
                                        color = color.copy(alpha = 0.5f),
                                        start = Offset(px, 0f),
                                        end = Offset(px, h),
                                        strokeWidth = 1.5f
                                    )
                                }
                            }
                        }

                        // 현재 시간 수직 커서 라인 (timeOffset 보정 락킹)
                        val cursorTargetMs = (currentTimestampMs + timeOffsetMs - baseTime).coerceIn(0L, durationMs).toFloat()
                        val cursorX = (cursorTargetMs / totalTime) * w

                        drawLine(
                            color = Color.White,
                            start = Offset(cursorX, 0f),
                            end = Offset(cursorX, h),
                            strokeWidth = 2.5f
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(cursorX, 4f)
                        )
                    } else {
                        // 데이터 없음 가이드선
                        drawLine(
                            color = Color.DarkGray,
                            start = Offset(0f, h / 2f),
                            end = Offset(w, h / 2f),
                            strokeWidth = 1f
                        )
                    }
                }
            }
        }
    }
}
