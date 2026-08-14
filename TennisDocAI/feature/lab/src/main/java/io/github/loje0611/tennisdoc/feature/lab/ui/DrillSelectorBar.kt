package io.github.loje0611.tennisdoc.feature.lab.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        DrillType.FOREHAND_TOPSPIN,
        DrillType.FOREHAND_FLAT,
        DrillType.FOREHAND_SLICE,
        DrillType.BACKHAND_TOPSPIN,
        DrillType.BACKHAND_FLAT,
        DrillType.BACKHAND_SLICE,
        DrillType.SERVE,
        DrillType.VOLLEY
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        drillOptions.forEach { drill ->
            val isSelected = drill == selectedDrill
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (!isSessionActive) {
                        onSelectDrill(drill)
                    }
                },
                enabled = !isSessionActive,
                label = {
                    Text(
                        text = drill.toDisplayName(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

fun DrillType.toDisplayName(): String {
    return when (this) {
        DrillType.FOREHAND_TOPSPIN -> "포핸드 탑스핀"
        DrillType.FOREHAND_FLAT -> "포핸드 플랫"
        DrillType.FOREHAND_SLICE -> "포핸드 슬라이스"
        DrillType.BACKHAND_TOPSPIN -> "백핸드 탑스핀"
        DrillType.BACKHAND_FLAT -> "백핸드 플랫"
        DrillType.BACKHAND_SLICE -> "백핸드 슬라이스"
        DrillType.SERVE -> "서브"
        DrillType.VOLLEY -> "발리"
    }
}
