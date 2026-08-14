package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.loje0611.tennisdoc.core.fusion.anomaly.AnomalySeverity
import io.github.loje0611.tennisdoc.core.fusion.anomaly.BaselineComparisonReport

@Composable
fun LabAnomalyAlertBanner(
    report: BaselineComparisonReport?,
    modifier: Modifier = Modifier
) {
    if (report == null) return

    val isFatigued = report.fatigue.isFatigued
    val criticalAnomaly = report.anomalies.firstOrNull { it.severity == AnomalySeverity.CRITICAL }

    if (!isFatigued && criticalAnomaly == null) return

    val alertText = when {
        isFatigued -> report.fatigue.formBreakdownSummary ?: "⚠️ 세션 피로도가 감지되었습니다. 휴식을 권장합니다."
        criticalAnomaly != null -> "⚠️ ${criticalAnomaly.description}"
        else -> ""
    }

    if (alertText.isBlank()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                color = Color(0xFFC62828).copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = alertText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}
