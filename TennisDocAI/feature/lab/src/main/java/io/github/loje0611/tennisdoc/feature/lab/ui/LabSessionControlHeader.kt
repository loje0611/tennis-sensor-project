package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.loje0611.tennisdoc.core.model.DrillType

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
    onConnectSensor: () -> Unit = {},
    onCancelSensorConnect: () -> Unit = {}
) {
    val statusText = when {
        isSensorConnected -> "센서 연결됨"
        isSensorScanning -> "센서 찾는 중..."
        else -> "센서 미연결"
    }
    val statusColor = when {
        isSensorConnected -> Color(0xFF4CAF50)
        isSensorScanning -> Color(0xFFFFC107)
        else -> Color(0xFFE53935)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !isSensorConnected) {
                            if (isSensorScanning) onCancelSensorConnect() else onConnectSensor()
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color = statusColor, shape = CircleShape)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }

                if (isSessionActive) {
                    val minutes = sessionDurationSeconds / 60
                    val seconds = sessionDurationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d | 스윙 %d회", minutes, seconds, swingCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "목표: ${selectedDrill.toDisplayName()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isSensorConnected) {
                    Button(
                        onClick = {
                            if (isSensorScanning) onCancelSensorConnect() else onConnectSensor()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSensorScanning) Color(0xFF5D4037) else Color(0xFF1565C0)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isSensorScanning) "연결 취소" else "센서 연결",
                            color = Color.White
                        )
                    }
                }
                Button(
                    onClick = {
                        if (isSessionActive) onFinishSession() else onStartSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSessionActive) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isSessionActive) "측정 종료" else "측정 시작",
                        color = Color.White
                    )
                }
            }
        }
    }
}
