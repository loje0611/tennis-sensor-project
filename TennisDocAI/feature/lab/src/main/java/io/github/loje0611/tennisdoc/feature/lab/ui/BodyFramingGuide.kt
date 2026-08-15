package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BodyFramingGuide(
    isFrontCamera: Boolean,
    isSessionActive: Boolean,
    isBodyFramed: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isFrontCamera || isSessionActive) return

    val statusColor = if (isBodyFramed) Color(0xFF10B981) else Color(0xFFF59E0B)
    val statusText = if (isBodyFramed) "🟢 READY (준비 완료)" else "🟡 카메라 안에 전신을 맞춰주세요"

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Semi-transparent framing box
        Box(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .border(
                    width = 2.5.dp,
                    color = statusColor.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp)
                )
        )

        // Status pill indicator
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .background(
                    color = Color(0xF2FFFFFF),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = statusColor,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1E)
            )
        }
    }
}
