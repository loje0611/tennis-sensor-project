package io.github.loje0611.tennisdoc.core.fusion.coaching

import io.github.loje0611.tennisdoc.core.fusion.model.FusionDiagnosis
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

class CausalCoachingEngine {

    fun diagnose(
        drillType: DrillType,
        anchor: SyncAnchor,
        kineticChain: KineticChain5Stage,
        racketImpact: RacketImpactOrientation,
        poses: List<PoseFrame>,
        imuSamples: List<ImuDataPoint>
    ): FusionDiagnosis {
        if (!anchor.isSynchronized) {
            return FusionDiagnosis(
                diagnosisTags = listOf("SYNC_FAILED"),
                primaryCause = "센서-비전 임팩트 시간 동기화 실패",
                coachingFeedback = "센서 연결 및 카메라 앵글을 확인하고 다시 스윙해 주세요.",
                causalExplanation = "센서 충격 피크와 비전 손목 가속 피크 간의 시간차가 허용 범위를 초과하여 신뢰성 있는 융합 분석을 수행할 수 없습니다."
            )
        }

        val stages = kineticChain.stages
        val hipTs = stages.firstOrNull { it.stage == KineticStageType.HIP }?.peakTimestampMs ?: 0L
        val shoulderTs = stages.firstOrNull { it.stage == KineticStageType.SHOULDER }?.peakTimestampMs ?: 0L
        val wristTs = stages.firstOrNull { it.stage == KineticStageType.WRIST }?.peakTimestampMs ?: 0L
        val racketTs = stages.firstOrNull { it.stage == KineticStageType.RACKET }?.peakTimestampMs ?: 0L

        val isEarlyBodyOpen = shoulderTs <= hipTs || (shoulderTs - hipTs < 10L)
        val hasChainTimingDelay = (racketTs - wristTs > 70L) || (shoulderTs - hipTs > 90L)

        // Rule 1: 상체 조기 개방으로 인한 페이스 열림
        if (racketImpact.faceState == RacketFaceState.OPEN && (!kineticChain.isSequential || isEarlyBodyOpen)) {
            return FusionDiagnosis(
                diagnosisTags = listOf("FACE_OPEN", "EARLY_BODY_OPEN", "KINETIC_FAULT"),
                primaryCause = "상체 조기 회전으로 인한 타점 밀림 및 페이스 열림",
                coachingFeedback = "골반 회전이 먼저 시작된 후 상체가 따라오도록 코어 타이밍을 교정하세요.",
                causalExplanation = "상체가 골반보다 일찍 열려 임팩트 타점이 뒤로 밀리면서 라켓 페이스가 열린 상태로 맞았습니다."
            )
        }

        // Rule 2: 후방 타점으로 인한 페이스 닫힘
        if (racketImpact.faceState == RacketFaceState.CLOSED) {
            return FusionDiagnosis(
                diagnosisTags = listOf("FACE_CLOSED", "LATE_CONTACT"),
                primaryCause = "타점 후방 형성으로 인한 라켓 페이스 닫힘",
                coachingFeedback = "몸 앞쪽에서 공을 맞추도록 전방 타점을 확보하고 팔로우 스루를 길게 가져가세요.",
                causalExplanation = "임팩트 타점이 몸 뒤에서 형성되어 라켓 헤드가 급격히 감기며 페이스가 닫혔습니다."
            )
        }

        // Rule 3: 운동 체인 에너지 유실
        if (kineticChain.energyTransferEfficiency < 65.0f && (hasChainTimingDelay || !kineticChain.isSequential)) {
            return FusionDiagnosis(
                diagnosisTags = listOf("POWER_LEAK", "CHAIN_TIMING_DELAY"),
                primaryCause = "신체 분절 간 가속 전달 지연으로 인한 스윙 파워 손실",
                coachingFeedback = "손목과 라켓의 릴리즈 타이밍을 일치시켜 채찍처럼 에너지를 전달하세요.",
                causalExplanation = "상체 회전 에너지가 라켓 헤드로 전달되는 과정에서 지연이 발생해 스윙 파워가 감소했습니다."
            )
        }

        // Rule 4: 클린 스트라이크
        if (kineticChain.isSequential && racketImpact.faceState == RacketFaceState.SQUARE && kineticChain.energyTransferEfficiency >= 80.0f) {
            return FusionDiagnosis(
                diagnosisTags = listOf("CLEAN_STRIKE", "OPTIMAL_CHAIN", "SQUARE_FACE"),
                primaryCause = "완벽한 5단계 운동 체인 및 스퀘어 임팩트",
                coachingFeedback = "이상적인 체인 타이밍과 정확한 페이스 정렬입니다. 현재 폼을 유지하세요.",
                causalExplanation = "골반부터 라켓까지 순차적으로 가속되어 최대의 에너지가 스퀘어 페이스로 공에 전달되었습니다."
            )
        }

        // Fallback: 일반 상태
        return when (racketImpact.faceState) {
            RacketFaceState.OPEN -> FusionDiagnosis(
                diagnosisTags = listOf("FACE_OPEN"),
                primaryCause = "라켓 페이스 열림",
                coachingFeedback = "임팩트 시 라켓 면을 직각으로 유지하도록 손목 각도를 교정하세요.",
                causalExplanation = "임팩트 순간 라켓 페이스가 열린 상태로 공에 접촉했습니다."
            )
            RacketFaceState.CLOSED -> FusionDiagnosis(
                diagnosisTags = listOf("FACE_CLOSED"),
                primaryCause = "라켓 페이스 닫힘",
                coachingFeedback = "임팩트 시 라켓 면이 닫히지 않도록 전방 타점을 유지하세요.",
                causalExplanation = "임팩트 순간 라켓 페이스가 닫힌 상태로 공에 접촉했습니다."
            )
            RacketFaceState.SQUARE -> FusionDiagnosis(
                diagnosisTags = listOf("NEUTRAL_SWING"),
                primaryCause = "표준 스윙 궤적",
                coachingFeedback = "기본 스윙 자세를 유지하며 지속적으로 훈련하세요.",
                causalExplanation = "스윙 역학 및 라켓 페이스 상태가 기준 범위 내에 있습니다."
            )
        }
    }
}
