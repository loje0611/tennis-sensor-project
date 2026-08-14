package io.github.loje0611.tennisdoc.core.fusion.engine

import io.github.loje0611.tennisdoc.core.fusion.analysis.KineticChain5StageAnalyzer
import io.github.loje0611.tennisdoc.core.fusion.coaching.CausalCoachingEngine
import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.orientation.RacketImpactCalculator
import io.github.loje0611.tennisdoc.core.fusion.sync.ImpactAnchorSynchronizer
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import java.util.UUID

class FusionEngineImpl(
    private val synchronizer: ImpactAnchorSynchronizer = ImpactAnchorSynchronizer(),
    private val orientationCalculator: RacketImpactCalculator = RacketImpactCalculator(),
    private val kineticChainAnalyzer: KineticChain5StageAnalyzer = KineticChain5StageAnalyzer(),
    private val coachingEngine: CausalCoachingEngine = CausalCoachingEngine()
) : FusionEngine {

    override fun fuse(
        drillType: DrillType,
        poses: List<PoseFrame>,
        imuSamples: List<ImuDataPoint>
    ): FusedSwing {
        val anchor = synchronizer.synchronize(poses, imuSamples)
        val racketImpact = orientationCalculator.calculate(imuSamples, anchor)
        val kineticChain = kineticChainAnalyzer.analyze(poses, imuSamples, anchor)
        val diagnosis = coachingEngine.diagnose(drillType, anchor, kineticChain, racketImpact, poses, imuSamples)

        return FusedSwing(
            swingId = UUID.randomUUID().toString(),
            sessionId = "fusion-session-${System.currentTimeMillis()}",
            drillType = drillType,
            anchor = anchor,
            kineticChain = kineticChain,
            racketImpact = racketImpact,
            visionPoses = poses,
            imuSamples = imuSamples,
            diagnosis = diagnosis
        )
    }
}
