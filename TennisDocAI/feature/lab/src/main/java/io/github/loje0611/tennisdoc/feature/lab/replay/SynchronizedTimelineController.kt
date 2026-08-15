package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun SynchronizedTimelineController(
    currentTimestampMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSpeedToggle: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onJumpToImpact: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 시간 표시 & 임팩트 점프 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTime(currentTimestampMs)} / ${formatTime(durationMs)}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalButton(
                    onClick = onJumpToImpact,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFF5722),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("🎯 임팩트 점프", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 시크바 슬라이더
            Slider(
                value = currentTimestampMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 컨트롤 버튼 행
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 배속 토글 버튼
                OutlinedButton(
                    onClick = onSpeedToggle,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .size(width = 68.dp, height = 48.dp)
                ) {
                    Text(
                        text = if (playbackSpeed <= 0.5f) "0.5x" else "1.0x",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // -1 프레임 스텝
                OutlinedButton(
                    onClick = onStepBack,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 54.dp, height = 48.dp)
                ) {
                    Text("◀", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // 재생 / 일시정지
                ElevatedButton(
                    onClick = onTogglePlay,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.size(width = 80.dp, height = 48.dp)
                ) {
                    Text(
                        text = if (isPlaying) "정지" else "재생",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // +1 프레임 스텝
                OutlinedButton(
                    onClick = onStepForward,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 54.dp, height = 48.dp)
                ) {
                    Text("▶", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val ms = millis % 1000
    return String.format(Locale.US, "%02d.%03d", seconds, ms)
}
