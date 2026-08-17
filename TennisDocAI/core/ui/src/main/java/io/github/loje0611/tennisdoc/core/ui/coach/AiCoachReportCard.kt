package io.github.loje0611.tennisdoc.core.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.core.model.AiCoachReport
import io.github.loje0611.tennisdoc.core.model.DrillRecommendation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiCoachReportCard(
    report: AiCoachReport,
    modifier: Modifier = Modifier,
    onDrillClick: ((DrillRecommendation) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🤖 AI 코치 처방 리포트",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            
            val badgeText = if (report.isFallbackReport) "⚡ 로컬 룰 엔진 분석" else "✨ Gemini AI 분석"
            val badgeColor = if (report.isFallbackReport) Color(0xFF64748B) else Color(0xFF2563EB)
            val badgeBg = if (report.isFallbackReport) Color(0xFFF1F5F9) else Color(0xFFEFF6FF)
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(badgeBg)
                    .border(1.dp, badgeColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = badgeText, fontSize = 10.sp, color = badgeColor, fontWeight = FontWeight.Medium)
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        val dateString = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(report.generatedAtMillis))
        Text(text = dateString, fontSize = 12.sp, color = Color(0xFF94A3B8))
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Overall Summary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = report.overallSummary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Color(0xFF334155)
            )
        }
        
        // Key Strengths
        if (report.keyStrengths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "💪 강점", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                report.keyStrengths.forEach { strength ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF0FDF4))
                            .border(1.dp, Color(0xFF16A34A), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "✓", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(text = strength, color = Color(0xFF16A34A), fontSize = 12.sp)
                    }
                }
            }
        }

        // Causal Diagnosis
        if (report.primaryFlawDiagnosis != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "🔍 정밀 분석 및 인과 진단", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))
            CausalDiagnosisCard(diagnosis = report.primaryFlawDiagnosis!!)
        }

        // Action Items
        if (report.actionItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "📋 집중 과제", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.actionItems.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(text = "${index + 1}.", fontSize = 14.sp, color = Color(0xFF64748B), modifier = Modifier.padding(end = 6.dp))
                        Text(text = item, fontSize = 14.sp, color = Color(0xFF334155), lineHeight = 20.sp)
                    }
                }
            }
        }

        // Recommended Drills
        if (report.recommendedDrills.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "🎯 다음 세션 추천 드릴", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.recommendedDrills.forEach { drill ->
                    DrillRecommendationCard(drill = drill, onClick = { onDrillClick?.invoke(drill) })
                }
            }
        }
    }
}
