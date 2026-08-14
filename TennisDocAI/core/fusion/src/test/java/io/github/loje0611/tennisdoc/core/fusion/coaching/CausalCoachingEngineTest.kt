package io.github.loje0611.tennisdoc.core.fusion.coaching

import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CausalCoachingEngineTest {

    private val engine = CausalCoachingEngine()

    private fun createValid5Stages(
        hipTs: Long = 1000L,
        shoulderTs: Long = 1040L,
        wristTs: Long = 1080L,
        racketTs: Long = 1110L,
        impactTs: Long = 1130L
    ) = listOf(
        KineticStage(KineticStageType.HIP, hipTs, 400f),
        KineticStage(KineticStageType.SHOULDER, shoulderTs, 600f, shoulderTs - hipTs),
        KineticStage(KineticStageType.WRIST, wristTs, 900f, wristTs - shoulderTs),
        KineticStage(KineticStageType.RACKET, racketTs, 1500f, racketTs - wristTs),
        KineticStage(KineticStageType.IMPACT, impactTs, 20f, impactTs - racketTs)
    )

    @Test
    fun `AC-2 early body open with open face triggers Rule 1 diagnosis`() {
        val anchor = SyncAnchor(1130L, 1130L, 0L, 0.95f, true)
        // Shoulder peaks before Hip (shoulder 1000, hip 1040)
        val stages = createValid5Stages(hipTs = 1040L, shoulderTs = 1000L)
        val chain = KineticChain5Stage(stages, isSequential = false, totalDurationMs = 130L, energyTransferEfficiency = 45f)
        val racketImpact = RacketImpactOrientation(12f, 0f, 0f, RacketFaceState.OPEN, 12f)

        val diagnosis = engine.diagnose(
            DrillType.FOREHAND_TOPSPIN,
            anchor,
            chain,
            racketImpact,
            emptyList(),
            emptyList()
        )

        assertTrue(diagnosis.diagnosisTags.contains("FACE_OPEN"))
        assertTrue(diagnosis.diagnosisTags.contains("EARLY_BODY_OPEN"))
        assertEquals("상체 조기 회전으로 인한 타점 밀림 및 페이스 열림", diagnosis.primaryCause)
        assertTrue(diagnosis.causalExplanation.contains("상체가 골반보다 일찍 열려"))
    }

    @Test
    fun `AC-3 late contact with closed face triggers Rule 2 diagnosis`() {
        val anchor = SyncAnchor(1130L, 1130L, 0L, 0.95f, true)
        val stages = createValid5Stages()
        val chain = KineticChain5Stage(stages, isSequential = true, totalDurationMs = 130L, energyTransferEfficiency = 85f)
        val racketImpact = RacketImpactOrientation(-12f, 0f, 0f, RacketFaceState.CLOSED, -12f)

        val diagnosis = engine.diagnose(
            DrillType.FOREHAND_FLAT,
            anchor,
            chain,
            racketImpact,
            emptyList(),
            emptyList()
        )

        assertTrue(diagnosis.diagnosisTags.contains("FACE_CLOSED"))
        assertTrue(diagnosis.diagnosisTags.contains("LATE_CONTACT"))
        assertEquals("타점 후방 형성으로 인한 라켓 페이스 닫힘", diagnosis.primaryCause)
        assertTrue(diagnosis.coachingFeedback.contains("몸 앞쪽에서 공을 맞추도록"))
    }

    @Test
    fun `Rule 3 power leak triggers when efficiency is low and timing delay exists`() {
        val anchor = SyncAnchor(1130L, 1130L, 0L, 0.95f, true)
        // Delayed shoulder (delay 100ms)
        val stages = createValid5Stages(hipTs = 1000L, shoulderTs = 1100L)
        val chain = KineticChain5Stage(stages, isSequential = true, totalDurationMs = 150L, energyTransferEfficiency = 55f)
        val racketImpact = RacketImpactOrientation(0f, 0f, 0f, RacketFaceState.SQUARE, 0f)

        val diagnosis = engine.diagnose(
            DrillType.FOREHAND_TOPSPIN,
            anchor,
            chain,
            racketImpact,
            emptyList(),
            emptyList()
        )

        assertTrue(diagnosis.diagnosisTags.contains("POWER_LEAK"))
        assertTrue(diagnosis.diagnosisTags.contains("CHAIN_TIMING_DELAY"))
        assertEquals("신체 분절 간 가속 전달 지연으로 인한 스윙 파워 손실", diagnosis.primaryCause)
    }

    @Test
    fun `AC-4 clean strike triggers Rule 4 diagnosis`() {
        val anchor = SyncAnchor(1130L, 1130L, 0L, 0.95f, true)
        val stages = createValid5Stages()
        val chain = KineticChain5Stage(stages, isSequential = true, totalDurationMs = 130L, energyTransferEfficiency = 95f)
        val racketImpact = RacketImpactOrientation(0f, 0f, 0f, RacketFaceState.SQUARE, 0f)

        val diagnosis = engine.diagnose(
            DrillType.FOREHAND_TOPSPIN,
            anchor,
            chain,
            racketImpact,
            emptyList(),
            emptyList()
        )

        assertTrue(diagnosis.diagnosisTags.contains("CLEAN_STRIKE"))
        assertTrue(diagnosis.diagnosisTags.contains("OPTIMAL_CHAIN"))
        assertTrue(diagnosis.diagnosisTags.contains("SQUARE_FACE"))
        assertEquals("완벽한 5단계 운동 체인 및 스퀘어 임팩트", diagnosis.primaryCause)
    }

    @Test
    fun `unsynchronized anchor triggers SYNC_FAILED diagnosis`() {
        val anchor = SyncAnchor(1000L, 1250L, 250L, 0.0f, false)
        val stages = createValid5Stages()
        val chain = KineticChain5Stage(stages, isSequential = false, totalDurationMs = 0L, energyTransferEfficiency = 0f)
        val racketImpact = RacketImpactOrientation(0f, 0f, 0f, RacketFaceState.SQUARE, 0f)

        val diagnosis = engine.diagnose(
            DrillType.FOREHAND_TOPSPIN,
            anchor,
            chain,
            racketImpact,
            emptyList(),
            emptyList()
        )

        assertTrue(diagnosis.diagnosisTags.contains("SYNC_FAILED"))
        assertEquals("센서-비전 임팩트 시간 동기화 실패", diagnosis.primaryCause)
    }
}
