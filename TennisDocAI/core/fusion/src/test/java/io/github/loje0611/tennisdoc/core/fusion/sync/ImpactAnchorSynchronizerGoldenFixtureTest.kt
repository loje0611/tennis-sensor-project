package io.github.loje0611.tennisdoc.core.fusion.sync

import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class ImpactAnchorSynchronizerGoldenFixtureTest {

    private val synchronizer = ImpactAnchorSynchronizer()

    @Test
    fun `golden JSON fixtures all pass`() {
        val fixtures = loadFixtures()
        assertTrue("golden fixture file must not be empty", fixtures.length() > 0)

        for (i in 0 until fixtures.length()) {
            val case = fixtures.getJSONObject(i)
            val name = case.getString("name")
            val poses = posesFromWristX(case.getJSONArray("wrist_x"))
            val imu = imuFromSpec(case.opt("imu"))
            val expected = case.getJSONObject("expected")

            val anchor = synchronizer.synchronize(
                poses = poses,
                imuSamples = imu,
                baseVisionTimestampMs = case.optLong("base_vision_timestamp_ms", 0L),
                visionFps = case.optDouble("vision_fps", 30.0).toFloat(),
                isRightHand = case.optBoolean("is_right_hand", true),
            )

            if (expected.has("vision_impact_timestamp_ms")) {
                assertEquals(
                    "$name visionImpactTimestampMs",
                    expected.getLong("vision_impact_timestamp_ms"),
                    anchor.visionImpactTimestampMs,
                )
            }
            if (expected.has("sensor_impact_timestamp_ms")) {
                assertEquals(
                    "$name sensorImpactTimestampMs",
                    expected.getLong("sensor_impact_timestamp_ms"),
                    anchor.sensorImpactTimestampMs,
                )
            }
            if (expected.has("time_offset_ms")) {
                assertEquals(
                    "$name timeOffsetMs",
                    expected.getLong("time_offset_ms"),
                    anchor.timeOffsetMs,
                )
            }
            assertEquals(
                "$name isSynchronized",
                expected.getBoolean("is_synchronized"),
                anchor.isSynchronized,
            )
            if (expected.has("confidence")) {
                assertEquals(
                    "$name confidence",
                    expected.getDouble("confidence").toFloat(),
                    anchor.confidence,
                    0.001f,
                )
            }
            if (expected.has("min_confidence")) {
                val min = expected.getDouble("min_confidence").toFloat()
                assertTrue(
                    "$name confidence ${anchor.confidence} < $min",
                    anchor.confidence >= min,
                )
            }
            if (expected.has("time_offset_ms") && expected.getBoolean("is_synchronized")) {
                val detectionError = abs(
                    anchor.timeOffsetMs - expected.getLong("time_offset_ms"),
                )
                assertTrue(
                    "$name sync error ${detectionError}ms exceeds 33ms",
                    detectionError <= 33L,
                )
            }
        }
    }

    private fun loadFixtures(): JSONArray {
        val uri = javaClass.classLoader?.getResource("golden_sync_anchor_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_sync_anchor_fixture.json")
        return JSONArray(File(uri.toURI()).readText())
    }

    private fun posesFromWristX(wristX: JSONArray): List<PoseFrame> {
        if (wristX.length() == 0) return emptyList()
        return (0 until wristX.length()).map { frameIdx ->
            val x = wristX.getDouble(frameIdx).toFloat()
            val landmarks = (0..32).map { jointIdx ->
                if (jointIdx == 16) {
                    PoseLandmark(x, 0.5f, 0.0f, 1.0f)
                } else {
                    PoseLandmark(0.5f, 0.5f, 0.0f, 1.0f)
                }
            }
            PoseFrame(landmarks)
        }
    }

    private fun imuFromSpec(raw: Any?): List<ImuDataPoint> {
        if (raw == null || raw == JSONObject.NULL) return emptyList()
        val spec = raw as JSONObject
        val start = spec.getLong("start_ms")
        val duration = spec.getLong("duration_ms")
        val step = spec.getLong("step_ms")
        val peakTs = spec.getLong("peak_timestamp_ms")
        val peakG = spec.getDouble("peak_accel_g").toFloat()
        val baseline = spec.getDouble("baseline_accel_g").toFloat()
        val secondaryTs = if (spec.has("secondary_peak_timestamp_ms")) {
            spec.getLong("secondary_peak_timestamp_ms")
        } else {
            null
        }
        val secondaryG = spec.optDouble("secondary_peak_accel_g", 0.0).toFloat()

        val samples = mutableListOf<ImuDataPoint>()
        var ts = start
        while (ts <= start + duration) {
            val accel = when (ts) {
                peakTs -> peakG
                secondaryTs -> secondaryG
                else -> baseline
            }
            samples.add(
                ImuDataPoint(
                    timestampMs = ts,
                    accelX = accel,
                    accelY = 0f,
                    accelZ = 0f,
                    gyroX = if (ts == peakTs) 1200f else 50f,
                    gyroY = 0f,
                    gyroZ = 0f,
                ),
            )
            ts += step
        }
        return samples
    }
}
