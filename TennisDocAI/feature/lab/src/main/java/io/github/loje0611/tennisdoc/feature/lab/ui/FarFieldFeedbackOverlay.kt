package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FarFieldFeedbackOverlay(
    hudState: FarFieldHudState?,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isFrontCamera || hudState == null) return

    val pulseColor = Color(hudState.faceColorHex)

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = 8.dp,
                color = pulseColor.copy(alpha = 0.85f),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.85f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .border(
                        width = 3.dp,
                        color = pulseColor,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = hudState.faceText,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = pulseColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "효율 ${hudState.energyEfficiency.toInt()}%",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (hudState.isSquare) "🟢 완벽한 정타 & 에너지 전달" else "🔴 페이스 각도 보정이 필요합니다",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
