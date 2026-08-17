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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.loje0611.tennisdoc.core.ui.theme.MichromaFont
import io.github.loje0611.tennisdoc.core.ui.coach.AiCoachReportCard
import io.github.loje0611.tennisdoc.core.ui.coach.AiCoachLoadingSkeleton

@Composable
fun SessionCompletionDialog(
    summary: SessionCompletionSummary?,
    aiReport: io.github.loje0611.tennisdoc.core.model.AiCoachReport? = null,
    isGeneratingAiReport: Boolean = false,
    onGenerateAiReport: () -> Unit = {},
    onDismiss: () -> Unit,
    onNavigateToReplay: (sessionId: String, recordId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (summary == null) return

    val minutes = summary.durationSeconds / 60
    val seconds = summary.durationSeconds % 60
    val durationText = String.format("%02d:%02d", minutes, seconds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🎯 ${summary.drillName} 훈련 완료!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1E)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "훈련이 성공적으로 기록되었습니다. 요약 결과를 확인하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF555560)
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8FAFC)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = Color(0x330066FF),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricRow(label = "총 스윙 수", value = "${summary.totalSwingCount}회", isMichroma = true)
                        MetricRow(label = "훈련 소요 시간", value = durationText, isMichroma = true)
                        MetricRow(
                            label = "정타율 (SQUARE)",
                            value = "${summary.squareRatePercent}%",
                            valueColor = Color(0xFF10B981),
                            isMichroma = true
                        )
                        MetricRow(
                            label = "평균 체인 효율",
                            value = String.format("%.1f%%", summary.averageEnergyEfficiency),
                            valueColor = Color(0xFF0066FF),
                            isMichroma = true
                        )
                    }
                }

                if (aiReport != null) {
                    AiCoachReportCard(report = aiReport)
                } else if (isGeneratingAiReport) {
                    AiCoachLoadingSkeleton()
                } else {
                    OutlinedButton(
                        onClick = onGenerateAiReport,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF8FAFC),
                            contentColor = Color(0xFF0066FF)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x330066FF))
                    ) {
                        Text(text = "🤖 AI 코치 처방받기", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onNavigateToReplay(summary.sessionId, summary.latestRecordId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "🎬 리플레이 보기", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "닫기 / 새 훈련", color = Color(0xFF555560))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    )
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFF1A1A1E),
    isMichroma: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF555560)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (isMichroma) MichromaFont else null,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
