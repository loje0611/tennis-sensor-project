package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.loje0611.tennisdoc.core.model.DrillType

@Composable
fun DrillSelectorBar(
    selectedDrill: DrillType,
    isSessionActive: Boolean,
    onSelectDrill: (DrillType) -> Unit,
    modifier: Modifier = Modifier
) {
    val drillOptions = listOf(
        DrillType.FOREHAND,
        DrillType.BACKHAND,
        DrillType.SERVE,
        DrillType.FOREHAND_VOLLEY,
        DrillType.BACKHAND_VOLLEY
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        drillOptions.forEach { drill ->
            val isSelected = drill == selectedDrill
            val capsuleShape = RoundedCornerShape(20.dp)

            Box(
                modifier = Modifier
                    .clip(capsuleShape)
                    .background(
                        color = if (isSelected) Color.White else Color(0xCCFFFFFF)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFF0066FF) else Color(0x220066FF),
                        shape = capsuleShape
                    )
                    .clickable(enabled = !isSessionActive) {
                        onSelectDrill(drill)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSelected) {
                        // Tennis Lime accent dot
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                    }

                    Text(
                        text = drill.toDisplayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF1A1A1E) else Color(0xFF555560)
                    )
                }
            }
        }
    }
}
