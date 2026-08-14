package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import kotlin.math.abs

@Composable
fun LabRealtimeFeedbackCard(
    fusedSwing: FusedSwing?,
    modifier: Modifier = Modifier
) {
    if (fusedSwing == null) return

    val impact = fusedSwing.racketImpact
    val chain = fusedSwing.kineticChain
    val diagnosis = fusedSwing.diagnosis

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. 라켓 페이스 상태 뱃지
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "임팩트 페이스",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                RacketFaceBadge(
                    faceState = impact.faceState,
                    deviationDeg = impact.deviationDeg
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. 5단계 운동 체인 게이지
            Text(
                text = "5단계 운동 체인 (에너지 전달: ${String.format("%.0f", chain.energyTransferEfficiency)}%)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            KineticChainFlowBar(isSequential = chain.isSequential)

            Spacer(modifier = Modifier.height(12.dp))

            // 3. 인과 코칭 텍스트
            if (diagnosis != null) {
                if (diagnosis.causalExplanation.isNotBlank()) {
                    Text(
                        text = "💡 ${diagnosis.causalExplanation}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFD54F)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (diagnosis.coachingFeedback.isNotBlank()) {
                    Text(
                        text = "🎯 ${diagnosis.coachingFeedback}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun RacketFaceBadge(
    faceState: RacketFaceState,
    deviationDeg: Float
) {
    val (badgeText, badgeColor) = when (faceState) {
        RacketFaceState.SQUARE -> "스퀘어 (${String.format("%.0f", abs(deviationDeg))}°)" to Color(0xFF4CAF50)
        RacketFaceState.OPEN -> "열림 (+${String.format("%.0f", abs(deviationDeg))}°)" to Color(0xFFFF9800)
        RacketFaceState.CLOSED -> "닫힘 (-${String.format("%.0f", abs(deviationDeg))}°)" to Color(0xFFE53935)
    }

    Box(
        modifier = Modifier
            .background(color = badgeColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

@Composable
private fun KineticChainFlowBar(isSequential: Boolean) {
    val stages = listOf("골반", "어깨", "손목", "라켓", "임팩트")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, name ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSequential) Color(0xFF81C784) else Color(0xFFFFB74D)
            )
            if (index < stages.size - 1) {
                Text(
                    text = "➔",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
