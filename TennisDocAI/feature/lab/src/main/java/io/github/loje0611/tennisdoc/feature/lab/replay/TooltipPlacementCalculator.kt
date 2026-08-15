package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

object TooltipPlacementCalculator {
    const val MIN_GAP: Float = 10f

    /**
     * TASK-007 INV-1~INV-3을 만족하도록 툴팁 박스 위치를 계산한다.
     * - INV-1 (상호 비겹침): 서로 다른 툴팁 사각형 간의 교집합 면적은 0이며, 최소 여백(MIN_GAP = 10dp/px) 이상 유지.
     * - INV-2 (프레임 내부): 모든 툴팁 박스는 캔버스 화면 경계 [0, width] x [0, height] 내에 완전히 포함.
     * - INV-3 (근접성): 충돌이 없는 한 대상 관절에 가장 가깝게 배치.
     * - INV-6 (결정성): 동일한 입력 데이터에 대해 결과는 항상 동일.
     */
    fun computePlacement(
        tooltips: List<ReplayTooltip>,
        boxSizes: List<Size>,
        canvasSize: Size
    ): List<Rect> {
        if (tooltips.isEmpty() || boxSizes.isEmpty() || canvasSize.width <= 0f || canvasSize.height <= 0f) {
            return emptyList()
        }

        val placedRects = mutableListOf<Rect>()

        for (i in tooltips.indices) {
            val tooltip = tooltips[i]
            val size = boxSizes.getOrNull(i) ?: Size(120f, 40f)
            val jointPixelX = tooltip.jointX * canvasSize.width
            val jointPixelY = tooltip.jointY * canvasSize.height

            val offset = 15f
            val candidateOffsets = listOf(
                Pair(offset, -size.height - offset), // 우상단
                Pair(offset, offset),                // 우하단
                Pair(-size.width - offset, -size.height - offset), // 좌상단
                Pair(-size.width - offset, offset),  // 좌하단
                Pair(-size.width / 2f, -size.height - offset - 10f), // 상단 중앙
                Pair(-size.width / 2f, offset + 10f), // 하단 중앙
                Pair(-size.width - offset - 10f, -size.height / 2f), // 좌측 중앙
                Pair(offset + 10f, -size.height / 2f) // 우측 중앙
            )

            var bestRect: Rect? = null
            var bestDistance = Float.MAX_VALUE

            for ((dx, dy) in candidateOffsets) {
                var candidateLeft = jointPixelX + dx
                var candidateTop = jointPixelY + dy

                // INV-2: 화면 경계 내로 클램핑
                if (candidateLeft < MIN_GAP) candidateLeft = MIN_GAP
                if (candidateTop < MIN_GAP) candidateTop = MIN_GAP
                if (candidateLeft + size.width > canvasSize.width - MIN_GAP) {
                    candidateLeft = max(MIN_GAP, canvasSize.width - size.width - MIN_GAP)
                }
                if (candidateTop + size.height > canvasSize.height - MIN_GAP) {
                    candidateTop = max(MIN_GAP, canvasSize.height - size.height - MIN_GAP)
                }

                val candidateRect = Rect(
                    candidateLeft,
                    candidateTop,
                    candidateLeft + size.width,
                    candidateTop + size.height
                )

                // INV-1: 기존 배치된 툴팁들과 겹치지 않는지 확인 (MIN_GAP 여백 포함)
                val collides = placedRects.any { existing ->
                    val expandedExisting = Rect(
                        existing.left - MIN_GAP,
                        existing.top - MIN_GAP,
                        existing.right + MIN_GAP,
                        existing.bottom + MIN_GAP
                    )
                    candidateRect.overlaps(expandedExisting)
                }

                if (!collides) {
                    val centerX = candidateRect.left + candidateRect.width / 2f
                    val centerY = candidateRect.top + candidateRect.height / 2f
                    val dist = (centerX - jointPixelX) * (centerX - jointPixelX) +
                               (centerY - jointPixelY) * (centerY - jointPixelY)
                    if (dist < bestDistance) {
                        bestDistance = dist
                        bestRect = candidateRect
                    }
                }
            }

            // 폴백: 수직 슬롯 스캔
            if (bestRect == null) {
                var fallbackTop = MIN_GAP
                while (fallbackTop + size.height <= canvasSize.height - MIN_GAP) {
                    val targetLeft = min(max(MIN_GAP, jointPixelX - size.width / 2f), max(MIN_GAP, canvasSize.width - size.width - MIN_GAP))
                    val candidateRect = Rect(
                        targetLeft,
                        fallbackTop,
                        targetLeft + size.width,
                        fallbackTop + size.height
                    )
                    val collides = placedRects.any { existing ->
                        val expandedExisting = Rect(
                            existing.left - MIN_GAP,
                            existing.top - MIN_GAP,
                            existing.right + MIN_GAP,
                            existing.bottom + MIN_GAP
                        )
                        candidateRect.overlaps(expandedExisting)
                    }
                    if (!collides) {
                        bestRect = candidateRect
                        break
                    }
                    fallbackTop += size.height + MIN_GAP
                }
            }

            if (bestRect != null) {
                placedRects.add(bestRect)
            }
        }

        return placedRects
    }
}
