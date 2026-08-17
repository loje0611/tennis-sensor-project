package io.github.loje0611.tennisdoc.core.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.core.model.CausalFlawDiagnosis

@Composable
fun CausalDiagnosisCard(
    diagnosis: CausalFlawDiagnosis,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFBEB))
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "🚨", fontSize = 14.sp)
            Column {
                Text("관측된 현상", fontSize = 12.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                Text(diagnosis.observedEffect, fontSize = 14.sp, color = Color(0xFF92400E))
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "🔍", fontSize = 14.sp)
            Column {
                Text("근본 원인", fontSize = 12.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                Text(diagnosis.rootCause, fontSize = 14.sp, color = Color(0xFF92400E))
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEFF6FF))
                .padding(12.dp)
        ) {
            Text(text = "💡", fontSize = 14.sp)
            Column {
                Text("코칭 큐", fontSize = 12.sp, color = Color(0xFF1E40AF), fontWeight = FontWeight.Bold)
                Text(diagnosis.coachingCue, fontSize = 14.sp, color = Color(0xFF1E40AF))
            }
        }
    }
}
