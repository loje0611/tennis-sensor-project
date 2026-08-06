package io.github.loje0611.tennisdoc.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.analysis.SwingMetrics
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private data class MetricDelta(
    val shortLabel: String,
    val deltaPercent: Double,
)

/**
 * (오늘 세션 평균 − 과거 누적 평균) 기준 변화율이 가장 큰 지표 상위 1~2개를 알약 칩으로 표시.
 * [history]가 없으면 아무것도 그리지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeltaSummaryChips(
    current: SwingMetrics,
    history: SwingMetrics?,
    modifier: Modifier = Modifier,
) {
    val chips = remember(current, history) {
        if (history == null) return@remember emptyList()
        val pairs = listOf(
            "파워" to pctDelta(current.power, history.power),
            "스핀" to pctDelta(current.spin, history.spin),
            "타이밍" to pctDelta(current.timing, history.timing),
            "부드러움" to pctDelta(current.smoothness, history.smoothness),
            "안정성" to pctDelta(current.stability, history.stability),
            "일관성" to pctDelta(current.consistency, history.consistency),
        )
            .map { (label, d) -> MetricDelta(label, d) }
            .filter { abs(it.deltaPercent) >= 0.5 } // 너무 미세한 변화 제외
            .sortedByDescending { abs(it.deltaPercent) }
            .take(2)
        pairs
    }

    if (chips.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val up = chip.deltaPercent > 0
            val icon = if (up) "🔥" else "⚠️"
            val sign = if (chip.deltaPercent > 0) "+" else ""
            val pct = chip.deltaPercent.roundToInt().coerceIn(-999, 999)
            val bg = if (up) SwingTheme.colors.successChipBg else SwingTheme.colors.warningChipBg
            val fg = if (up) SwingTheme.colors.successChipFg else SwingTheme.colors.warningChipFg

            Text(
                text = "$icon ${chip.shortLabel} $sign$pct%",
                modifier = Modifier
                    .background(bg, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}

/** 과거 대비 변화율(%). 과거가 0이면 현재만 반영해 대략적 % 표현. */
private fun pctDelta(current: Int, historical: Int): Double {
    if (historical == 0) return if (current == 0) 0.0 else 100.0
    return (current - historical) * 100.0 / historical
}
