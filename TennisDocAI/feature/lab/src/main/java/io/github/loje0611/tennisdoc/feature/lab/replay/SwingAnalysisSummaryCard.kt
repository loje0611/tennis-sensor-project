package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.core.ui.theme.MichromaFont
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme

@Composable
fun SwingAnalysisSummaryCard(
    swingPathType: String,
    faceStateLabel: String,
    coachingOneLiner: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SwingTheme.colors.cardSurface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SwingTheme.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "🎾 스윙 궤적 분석",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = MichromaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SwingTheme.colors.onBackground
                )
            )
            SummaryRow(
                label = "🎾 궤적 유형",
                value = swingPathType.ifBlank { "알 수 없음" }
            )
            SummaryRow(
                label = "🎯 임팩트 면",
                value = faceStateLabel.ifBlank { "-" }
            )
            SummaryRow(
                label = "💡 원포인트 코칭",
                value = coachingOneLiner.ifBlank { "분석 데이터가 없습니다." }
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = SwingTheme.colors.subGray
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = SwingTheme.colors.onBackground
            )
        )
    }
}
