package io.github.loje0611.tennisdoc.core.fusion.engine

import io.github.loje0611.tennisdoc.core.fusion.model.FusedSwing
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark

object LabRawRecordParser {

    private val imuRegex = Regex(
        """"ts"\s*:\s*(-?\d+)\s*,\s*"ax"\s*:\s*(-?[\d.]+)\s*,\s*"ay"\s*:\s*(-?[\d.]+)\s*,\s*"az"\s*:\s*(-?[\d.]+)\s*,\s*"gx"\s*:\s*(-?[\d.]+)\s*,\s*"gy"\s*:\s*(-?[\d.]+)\s*,\s*"gz"\s*:\s*(-?[\d.]+)"""
    )

    private val landmarkRegex = Regex(
        """"x"\s*:\s*(-?[\d.E+-]+)\s*,\s*"y"\s*:\s*(-?[\d.E+-]+)\s*,\s*"z"\s*:\s*(-?[\d.E+-]+)\s*,\s*"v"\s*:\s*(-?[\d.E+-]+)"""
    )

    fun parseImuSamples(json: String): List<ImuDataPoint> {
        if (json.isBlank()) return emptyList()
        val result = mutableListOf<ImuDataPoint>()
        for (match in imuRegex.findAll(json)) {
            val (ts, ax, ay, az, gx, gy, gz) = match.destructured
            result.add(
                ImuDataPoint(
                    timestampMs = ts.toLongOrNull() ?: 0L,
                    accelX = ax.toFloatOrNull() ?: 0f,
                    accelY = ay.toFloatOrNull() ?: 0f,
                    accelZ = az.toFloatOrNull() ?: 0f,
                    gyroX = gx.toFloatOrNull() ?: 0f,
                    gyroY = gy.toFloatOrNull() ?: 0f,
                    gyroZ = gz.toFloatOrNull() ?: 0f
                )
            )
        }
        return result
    }

    fun parsePoseFrames(json: String): List<PoseFrame> {
        if (json.isBlank()) return emptyList()
        val result = mutableListOf<PoseFrame>()
        val frameMatches = json.split("{\"landmarks\":")
        for (frameStr in frameMatches) {
            if (!frameStr.contains("\"x\"")) continue
            val landmarks = mutableListOf<PoseLandmark>()
            for (lmMatch in landmarkRegex.findAll(frameStr)) {
                val (x, y, z, v) = lmMatch.destructured
                landmarks.add(
                    PoseLandmark(
                        x = x.toFloatOrNull() ?: 0f,
                        y = y.toFloatOrNull() ?: 0f,
                        z = z.toFloatOrNull() ?: 0f,
                        visibility = v.toFloatOrNull() ?: 0f
                    )
                )
            }
            if (landmarks.isNotEmpty()) {
                result.add(PoseFrame(landmarks = landmarks))
            }
        }
        return result
    }

    fun parseFusedSwing(
        drillType: DrillType,
        imuJson: String,
        posesJson: String,
        fusionEngine: FusionEngine
    ): FusedSwing {
        val imuSamples = parseImuSamples(imuJson)
        val poses = parsePoseFrames(posesJson)
        return fusionEngine.fuse(drillType, poses, imuSamples)
    }
}
