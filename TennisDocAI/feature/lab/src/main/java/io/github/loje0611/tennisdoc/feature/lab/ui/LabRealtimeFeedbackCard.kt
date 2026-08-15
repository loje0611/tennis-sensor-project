package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
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

    // Clean Sunlit Court Pure White Sports HUD Card
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                color = Color(0xF8FFFFFF),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0x330066FF),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Racket Impact Face Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "임팩트 페이스",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF555560)
                )
                RacketFaceBadge(
                    faceState = impact.faceState,
                    deviationDeg = impact.deviationDeg
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 5-Stage Kinetic Chain Header & Gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "5단계 운동 체인",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF555560)
                )
                Text(
                    text = "전달 효율 ${String.format("%.0f", chain.energyTransferEfficiency)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0066FF)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            KineticChainFlowBar(isSequential = chain.isSequential)

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Causal Coaching Tip Box
            if (diagnosis != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFFFFFBEB), // Soft Yellow Tint
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFF59E0B).copy(alpha = 0.4f), // Amber Gold
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        if (diagnosis.causalExplanation.isNotBlank()) {
                            Text(
                                text = "💡 ${diagnosis.causalExplanation}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (diagnosis.coachingFeedback.isNotBlank()) {
                            Text(
                                text = "🎯 ${diagnosis.coachingFeedback}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1A1A1E)
                            )
                        }
                    }
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
        RacketFaceState.SQUARE -> "스퀘어 (${String.format("%.0f", abs(deviationDeg))}°)" to Color(0xFF10B981) // Vivid Emerald
        RacketFaceState.OPEN -> "열림 (+${String.format("%.0f", abs(deviationDeg))}°)" to Color(0xFFF59E0B) // Vivid Amber
        RacketFaceState.CLOSED -> "닫힘 (-${String.format("%.0f", abs(deviationDeg))}°)" to Color(0xFF3B82F6) // Royal Court Blue
    }

    Box(
        modifier = Modifier
            .background(color = badgeColor, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
    }
}

@Composable
private fun KineticChainFlowBar(isSequential: Boolean) {
    val stages = listOf("골반", "어깨", "손목", "라켓", "임팩트")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F4F8), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, name ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSequential) Color(0xFF059669) else Color(0xFFD97706)
            )
            if (index < stages.size - 1) {
                Text(
                    text = "➔",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF888890)
                )
            }
        }
    }
}
