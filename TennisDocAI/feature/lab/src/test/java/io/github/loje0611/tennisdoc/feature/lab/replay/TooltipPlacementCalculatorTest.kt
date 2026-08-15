package io.github.loje0611.tennisdoc.feature.lab.replay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TooltipPlacementCalculatorTest {

    @Test
    fun computePlacement_emptyInput_returnsEmptyList() {
        val result = TooltipPlacementCalculator.computePlacement(
            tooltips = emptyList(),
            boxSizes = emptyList(),
            canvasSize = Size(640f, 480f)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun computePlacement_satisfies_INV1_and_INV2_for_multiple_tooltips() {
        val canvasSize = Size(640f, 480f)
        val tooltips = listOf(
            ReplayTooltip(targetJointIndex = 24, jointX = 0.5f, jointY = 0.5f, text = "골반 조기 회전"),
            ReplayTooltip(targetJointIndex = 16, jointX = 0.52f, jointY = 0.48f, text = "페이스 열림 (12°)"),
            ReplayTooltip(targetJointIndex = 12, jointX = 0.48f, jointY = 0.45f, text = "어깨 가속 지연")
        )
        val boxSizes = listOf(
            Size(120f, 35f),
            Size(110f, 35f),
            Size(100f, 35f)
        )

        val rects = TooltipPlacementCalculator.computePlacement(tooltips, boxSizes, canvasSize)
        assertEquals(3, rects.size)

        // INV-2: 모든 사각형이 캔버스 경계 내에 완전히 포함되어야 함
        for (rect in rects) {
            assertTrue("left >= 0", rect.left >= 0f)
            assertTrue("top >= 0", rect.top >= 0f)
            assertTrue("right <= canvasWidth", rect.right <= canvasSize.width)
            assertTrue("bottom <= canvasHeight", rect.bottom <= canvasSize.height)
        }

        // INV-1: 쌍별 교집합 면적이 0이어야 함 (MIN_GAP 여백 준수)
        for (i in 0 until rects.size) {
            for (j in i + 1 until rects.size) {
                val r1 = rects[i]
                val r2 = rects[j]
                assertFalse("Rect $i and Rect $j must not overlap", r1.overlaps(r2))
                val inflated = Rect(
                    r1.left - TooltipPlacementCalculator.MIN_GAP,
                    r1.top - TooltipPlacementCalculator.MIN_GAP,
                    r1.right + TooltipPlacementCalculator.MIN_GAP,
                    r1.bottom + TooltipPlacementCalculator.MIN_GAP,
                )
                assertFalse(
                    "Rect $i and Rect $j must keep MIN_GAP=${TooltipPlacementCalculator.MIN_GAP}",
                    inflated.overlaps(r2),
                )
            }
        }
    }

    @Test
    fun computePlacement_satisfies_INV2_for_boundary_joints() {
        val canvasSize = Size(800f, 600f)
        val tooltips = listOf(
            ReplayTooltip(targetJointIndex = 0, jointX = 0.02f, jointY = 0.02f, text = "좌상단 경계"),
            ReplayTooltip(targetJointIndex = 1, jointX = 0.98f, jointY = 0.98f, text = "우하단 경계")
        )
        val boxSizes = listOf(Size(140f, 40f), Size(140f, 40f))

        val rects = TooltipPlacementCalculator.computePlacement(tooltips, boxSizes, canvasSize)
        assertEquals(2, rects.size)

        for (rect in rects) {
            assertTrue("left >= 0", rect.left >= 0f)
            assertTrue("top >= 0", rect.top >= 0f)
            assertTrue("right <= canvasWidth", rect.right <= canvasSize.width)
            assertTrue("bottom <= canvasHeight", rect.bottom <= canvasSize.height)
        }
    }

    @Test
    fun computePlacement_satisfies_INV6_determinism() {
        val canvasSize = Size(640f, 480f)
        val tooltips = listOf(
            ReplayTooltip(targetJointIndex = 24, jointX = 0.4f, jointY = 0.6f, text = "골반"),
            ReplayTooltip(targetJointIndex = 16, jointX = 0.6f, jointY = 0.4f, text = "손목")
        )
        val boxSizes = listOf(Size(100f, 30f), Size(100f, 30f))

        val run1 = TooltipPlacementCalculator.computePlacement(tooltips, boxSizes, canvasSize)
        val run2 = TooltipPlacementCalculator.computePlacement(tooltips, boxSizes, canvasSize)

        assertEquals(run1, run2)
    }
}
