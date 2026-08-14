package io.github.loje0611.tennisdoc.core.fusion.engine

import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

interface FusionEngine {
    fun fuse(
        drillType: DrillType,
        poses: List<PoseFrame>,
        imuSamples: List<ImuDataPoint>
    ): FusedSwing
}
