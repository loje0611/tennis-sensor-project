package io.github.loje0611.tennisdoc.core.fusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KineticChain5StageTest {

    private fun createValid5Stages(
        hipTs: Long = 1000L,
        shoulderTs: Long = 1040L,
        wristTs: Long = 1080L,
        racketTs: Long = 1110L,
        impactTs: Long = 1130L
    ): List<KineticStage> = listOf(
        KineticStage(KineticStageType.HIP, hipTs, 420.0f, 0L),
        KineticStage(KineticStageType.SHOULDER, shoulderTs, 580.0f, shoulderTs - hipTs),
        KineticStage(KineticStageType.WRIST, wristTs, 890.0f, wristTs - shoulderTs),
        KineticStage(KineticStageType.RACKET, racketTs, 1450.0f, racketTs - wristTs),
        KineticStage(KineticStageType.IMPACT, impactTs, 18.5f, impactTs - racketTs)
    )

    @Test
    fun `valid 5 stages creates KineticChain5Stage successfully`() {
        val stages = createValid5Stages()
        val chain = KineticChain5Stage(
            stages = stages,
            isSequential = true,
            totalDurationMs = 130L,
            energyTransferEfficiency = 88.5f
        )

        assertEquals(5, chain.stages.size)
        assertTrue(chain.isSequential)
        assertEquals(130L, chain.totalDurationMs)
        assertEquals(88.5f, chain.energyTransferEfficiency, 0.001f)
        assertEquals(KineticStageType.HIP, chain.stages[0].stage)
        assertEquals(KineticStageType.IMPACT, chain.stages[4].stage)
    }

    @Test
    fun `less than 5 stages throws IllegalArgumentException`() {
        val invalidStages = listOf(
            KineticStage(KineticStageType.HIP, 1000L, 400f),
            KineticStage(KineticStageType.SHOULDER, 1050L, 500f),
            KineticStage(KineticStageType.WRIST, 1100L, 600f)
        )

        assertThrows(IllegalArgumentException::class.java) {
            KineticChain5Stage(
                stages = invalidStages,
                isSequential = false,
                totalDurationMs = 100L,
                energyTransferEfficiency = 50f
            )
        }
    }

    @Test
    fun `more than 5 stages throws IllegalArgumentException`() {
        val invalidStages = createValid5Stages() + KineticStage(KineticStageType.IMPACT, 1200L, 20f)

        assertThrows(IllegalArgumentException::class.java) {
            KineticChain5Stage(
                stages = invalidStages,
                isSequential = false,
                totalDurationMs = 200L,
                energyTransferEfficiency = 40f
            )
        }
    }

    @Test
    fun `non sequential chain preserves flag correctly`() {
        // Wrist peaks before shoulder (broken kinetic chain)
        val stages = createValid5Stages(
            hipTs = 1000L,
            shoulderTs = 1090L,
            wristTs = 1050L,
            racketTs = 1110L,
            impactTs = 1130L
        )

        val chain = KineticChain5Stage(
            stages = stages,
            isSequential = false,
            totalDurationMs = 130L,
            energyTransferEfficiency = 45.0f
        )

        assertFalse(chain.isSequential)
        assertEquals(45.0f, chain.energyTransferEfficiency, 0.001f)
    }
}
