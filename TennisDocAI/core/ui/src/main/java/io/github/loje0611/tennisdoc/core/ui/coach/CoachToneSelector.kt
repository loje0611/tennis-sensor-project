package io.github.loje0611.tennisdoc.core.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.core.model.CoachTone

@Composable
fun CoachToneSelector(
    selectedTone: CoachTone,
    onToneSelected: (CoachTone) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CoachTone.entries.forEach { tone ->
            val isSelected = selectedTone == tone
            val backgroundColor = if (isSelected) Color(0xFF2563EB) else Color.Transparent
            val contentColor = if (isSelected) Color.White else Color(0xFF64748B)
            val borderColor = if (isSelected) Color.Transparent else Color(0xFFE2E8F0)
            
            val label = when (tone) {
                CoachTone.ENCOURAGING -> "🌱 격려형"
                CoachTone.ANALYTICAL -> "📊 분석형"
                CoachTone.STRICT -> "🎯 엄격형"
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onToneSelected(tone) }
                    .padding(vertical = 10.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
