package io.github.loje0611.tennisdoc.core.fusion.analysis

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KineticChain5StageAnalyzerGoldenFixtureTest {

    @Test
    fun `golden JSON fixtures all pass`() {
        val fixtures = loadFixtures()
        assertTrue("golden fixture file must not be empty", fixtures.length() > 0)

        for (i in 0 until fixtures.length()) {
            val case = fixtures.getJSONObject(i)
            val name = case.getString("name")
            val fps = case.getDouble("vision_fps").toFloat()
            val analyzer = KineticChain5StageAnalyzer(
                visionFps = fps,
                isRightHand = case.optBoolean("is_right_hand", true),
            )

            val poses = if (case.optBoolean("empty_poses", false)) {
                emptyList()
            } else {
                createPoseSequence(
                    totalFrames = case.getInt("total_frames"),
                    hipPeakFrame = case.getInt("hip_peak_frame"),
                    shoulderPeakFrame = case.getInt("shoulder_peak_frame"),
                    wristPeakFrame = case.getInt("wrist_peak_frame"),
                )
            }
            val imu = if (case.optBoolean("empty_imu", false)) {
                emptyList()
            } else {
                imuFromSpec(case.getJSONObject("imu"))
            }
            val anchorObj = case.getJSONObject("anchor")
            val anchor = SyncAnchor(
                visionImpactTimestampMs = anchorObj.getLong("vision_impact_timestamp_ms"),
                sensorImpactTimestampMs = anchorObj.getLong("sensor_impact_timestamp_ms"),
                timeOffsetMs = anchorObj.getLong("time_offset_ms"),
                confidence = anchorObj.getDouble("confidence").toFloat(),
                isSynchronized = anchorObj.getBoolean("is_synchronized"),
            )

            val first = analyzer.analyze(poses, imu, anchor)
            val second = analyzer.analyze(poses, imu, anchor)
            val expected = case.getJSONObject("expected")

            assertEquals("$name stage count", 5, first.stages.size)
            assertEquals(
                "$name deterministic timestamps",
                first.stages.map { it.peakTimestampMs },
                second.stages.map { it.peakTimestampMs },
            )
            assertEquals(
                "$name deterministic efficiency",
                first.energyTransferEfficiency,
                second.energyTransferEfficiency,
                0.001f,
            )

            val expectedTypes = expected.getJSONArray("stage_types")
            for (s in 0 until expectedTypes.length()) {
                assertEquals(
                    "$name stage[$s]",
                    KineticStageType.valueOf(expectedTypes.getString(s)),
                    first.stages[s].stage,
                )
            }

            assertEquals("$name isSequential", expected.getBoolean("is_sequential"), first.isSequential)

            if (expected.has("min_efficiency")) {
                val min = expected.getDouble("min_efficiency").toFloat()
                assertTrue(
                    "$name efficiency ${first.energyTransferEfficiency} < $min",
                    first.energyTransferEfficiency >= min,
                )
            }
            if (expected.has("max_efficiency")) {
                val max = expected.getDouble("max_efficiency").toFloat()
                assertTrue(
                    "$name efficiency ${first.energyTransferEfficiency} >= $max",
                    first.energyTransferEfficiency < max,
                )
            }
            if (expected.has("efficiency")) {
                assertEquals(
                    "$name efficiency",
                    expected.getDouble("efficiency").toFloat(),
                    first.energyTransferEfficiency,
                    0.001f,
                )
            }
            if (expected.has("timestamps_ms")) {
                val ts = expected.getJSONArray("timestamps_ms")
                for (s in 0 until ts.length()) {
                    assertEquals(
                        "$name timestamps_ms[$s]",
                        ts.getLong(s),
                        first.stages[s].peakTimestampMs,
                    )
                }
            }
            if (expected.has("racket_timestamp_ms")) {
                assertEquals(
                    "$name racket timestamp",
                    expected.getLong("racket_timestamp_ms"),
                    first.stages[3].peakTimestampMs,
                )
            }
            if (expected.has("impact_timestamp_ms")) {
                assertEquals(
                    "$name impact timestamp",
                    expected.getLong("impact_timestamp_ms"),
                    first.stages[4].peakTimestampMs,
                )
            }
            if (expected.has("total_duration_ms")) {
                assertEquals(
                    "$name totalDurationMs",
                    expected.getLong("total_duration_ms"),
                    first.totalDurationMs,
                )
            }
        }
    }

    private fun loadFixtures(): JSONArray {
        val uri = javaClass.classLoader?.getResource("golden_kinetic_chain_5stage_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_kinetic_chain_5stage_fixture.json")
        return JSONArray(File(uri.toURI()).readText())
    }

    private fun createPoseSequence(
        totalFrames: Int,
        hipPeakFrame: Int,
        shoulderPeakFrame: Int,
        wristPeakFrame: Int,
    ): List<PoseFrame> {
        return (0 until totalFrames).map { frameIdx ->
            val landmarks = (0..32).map { jointIdx ->
                val x = when (jointIdx) {
                    24 -> if (frameIdx == hipPeakFrame) 0.8f else 0.2f
                    12 -> if (frameIdx == shoulderPeakFrame) 0.8f else 0.2f
                    16 -> if (frameIdx == wristPeakFrame) 0.9f else 0.2f
                    else -> 0.5f
                }
                PoseLandmark(x, 0.5f, 0.0f, 1.0f)
            }
            PoseFrame(landmarks)
        }
    }

    private fun imuFromSpec(spec: JSONObject): List<ImuDataPoint> {
        val start = spec.getLong("start_ms")
        val duration = spec.getLong("duration_ms")
        val step = spec.getLong("step_ms")
        val racketTs = spec.getLong("racket_peak_ts")
        val impactTs = spec.getLong("impact_peak_ts")
        val samples = mutableListOf<ImuDataPoint>()
        var ts = start
        while (ts <= start + duration) {
            samples.add(
                ImuDataPoint(
                    timestampMs = ts,
                    accelX = if (ts == impactTs) 25f else 1f,
                    accelY = 0f,
                    accelZ = 0f,
                    gyroX = if (ts == racketTs) 1800f else 100f,
                    gyroY = 0f,
                    gyroZ = 0f,
                ),
            )
            ts += step
        }
        return samples
    }
}
