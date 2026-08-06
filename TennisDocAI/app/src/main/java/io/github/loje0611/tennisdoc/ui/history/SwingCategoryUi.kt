package io.github.loje0611.tennisdoc.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme

private val VOLLEY_REGEX = Regex("\\bvolley\\b", RegexOption.IGNORE_CASE)
private val TOPSPIN_REGEX = Regex("\\btopspin\\b", RegexOption.IGNORE_CASE)
private val SLICE_REGEX = Regex("\\bslice\\b", RegexOption.IGNORE_CASE)
private val FLAT_REGEX = Regex("\\bflat\\b", RegexOption.IGNORE_CASE)

/** 구종 카테고리 키 → 테마 인식 액센트 색상. 앱 전역에서 공통 사용. */
@Composable
@ReadOnlyComposable
fun accentColorForCategory(categoryKey: String): Color {
    val n = categoryKey.lowercase().replace('_', ' ')
    return when {
        VOLLEY_REGEX.containsMatchIn(n) -> SwingTheme.colors.neonVolley
        TOPSPIN_REGEX.containsMatchIn(n) -> SwingTheme.colors.neonGreenTopspin
        SLICE_REGEX.containsMatchIn(n) -> SwingTheme.colors.electricCyanSlice
        FLAT_REGEX.containsMatchIn(n) -> SwingTheme.colors.vividNeonOrangeFlat
        else -> SwingTheme.colors.onBackground
    }
}

/** 구종 카테고리 키 → 테마 인식 그라디언트 브러시. */
@Composable
@ReadOnlyComposable
fun brushForCategory(categoryKey: String): Brush {
    val n = categoryKey.lowercase().replace('_', ' ')
    return when {
        VOLLEY_REGEX.containsMatchIn(n) -> SwingTheme.colors.brushVolley
        TOPSPIN_REGEX.containsMatchIn(n) -> SwingTheme.colors.brushTopspin
        SLICE_REGEX.containsMatchIn(n) -> SwingTheme.colors.brushSlice
        FLAT_REGEX.containsMatchIn(n) -> SwingTheme.colors.brushFlat
        else -> SwingTheme.colors.brushFlat
    }
}

/** History/SessionDetail 프로그레스 바용 별칭. */
@Composable
@ReadOnlyComposable
internal fun progressColorForCategoryKey(categoryKey: String): Color =
    accentColorForCategory(categoryKey)

@Composable
@ReadOnlyComposable
internal fun progressBrushForCategoryKey(categoryKey: String): Brush =
    brushForCategory(categoryKey)

internal fun displayCategoryTitle(categoryKey: String): String {
    val parts = categoryKey.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return categoryKey
    return parts.joinToString(" ") { w ->
        w.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase() else c.toString()
        }
    }
}

internal fun formatDurationMillis(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    val h = sec / 3600L
    val m = (sec % 3600L) / 60L
    val s = sec % 60L
    return when {
        h > 0L -> "${h}h ${m}m"
        m > 0L -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
