package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.ui.theme.MichromaFont

@Composable
fun LabSessionControlHeader(
    selectedDrill: DrillType,
    isSessionActive: Boolean,
    sessionDurationSeconds: Long,
    swingCount: Int,
    isSensorConnected: Boolean,
    onStartSession: () -> Unit,
    onFinishSession: () -> Unit,
    modifier: Modifier = Modifier,
    isSensorScanning: Boolean = false,
    cameraFacingMode: CameraFacingMode = CameraFacingMode.FRONT,
    onToggleCameraFacing: () -> Unit = {},
    onConnectSensor: () -> Unit = {},
    onCancelSensorConnect: () -> Unit = {}
) {
    val statusText = when {
        isSensorConnected -> "센서 연결됨"
        isSensorScanning -> "센서 찾는 중..."
        else -> "센서 미연결"
    }
    val statusColor = when {
        isSensorConnected -> Color(0xFF10B981) // Tennis Lime Green
        isSensorScanning -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFEF4444) // Coral Red
    }

    // Breathing pulse for sensor status dot
    val infiniteTransition = rememberInfiniteTransition(label = "sensor_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Clean Sunlit Court Frosted White Glass Container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                color = Color(0xF2FFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0x330066FF),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                // Sensor connection status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clickable(enabled = !isSensorConnected && !isSensorScanning) {
                            onConnectSensor()
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(if (isSensorConnected || isSensorScanning) pulseScale else 1f)
                            .background(
                                color = statusColor.copy(alpha = if (isSensorConnected || isSensorScanning) pulseAlpha else 1f),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF555560)
                    )
                }

                if (isSessionActive) {
                    val minutes = sessionDurationSeconds / 60
                    val seconds = sessionDurationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d | 스윙 %d회", minutes, seconds, swingCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = MichromaFont,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1E),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "목표: ${selectedDrill.toDisplayName()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1E),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Camera Facing Toggle Capsule
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFF0F4F8),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0x330066FF),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !isSessionActive) { onToggleCameraFacing() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (cameraFacingMode == CameraFacingMode.FRONT) "🔄 전면" else "🔄 후면",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1E)
                    )
                }

                // Action Button with Vivid Gradient
                val buttonGradient = if (isSessionActive) {
                    Brush.horizontalGradient(listOf(Color(0xFFFF3B30), Color(0xFFFF6B6B))) // Vivid Coral Red
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF0066FF), Color(0xFF00AAFF))) // Royal Court Blue
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush = buttonGradient)
                        .clickable {
                            if (isSessionActive) onFinishSession() else onStartSession()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSessionActive) "측정 종료" else "측정 시작",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
