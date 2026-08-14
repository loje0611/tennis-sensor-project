package io.github.loje0611.tennisdoc.core.fusion.evaluation

import io.github.loje0611.tennisdoc.core.fusion.engine.FusionEngineImpl
import io.github.loje0611.tennisdoc.core.fusion.model.ImuDataPoint
import io.github.loje0611.tennisdoc.core.model.DrillType
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AblationEvaluatorGoldenDatasetTest {

    private val evaluator = AblationEvaluator(jaccardThreshold = 0.3f)
    private val engine = FusionEngineImpl()

    @Test
    fun `golden ablation dataset pass rate is 100 percent and average Jaccard distance meets threshold`() {
        val dataset = loadDataset()
        assertTrue("golden_ablation_dataset.json must contain at least 5 cases", dataset.size >= 5)

        val first = evaluator.evaluateDataset(dataset, engine)
        val second = evaluator.evaluateDataset(dataset, engine)

        assertEquals("deterministic passRate", first.passRate, second.passRate, 0.001f)
        assertEquals("deterministic averageJaccardDistance", first.averageJaccardDistance, second.averageJaccardDistance, 0.001f)

        assertEquals(dataset.size, first.totalCases)
        assertEquals(dataset.size, first.passedCases)
        assertEquals(1.0f, first.passRate, 0.001f)
        assertTrue(
            "Average Jaccard distance should be >= 0.3f, got ${first.averageJaccardDistance}",
            first.averageJaccardDistance >= 0.3f,
        )
    }

    private fun loadDataset(): List<AblationTestCase> {
        val uri = javaClass.classLoader?.getResource("golden_ablation_dataset.json")
            ?: throw IllegalStateException("Cannot find golden_ablation_dataset.json")
        val arr = JSONArray(File(uri.toURI()).readText())
        return (0 until arr.length()).map { i -> parseCase(arr.getJSONObject(i)) }
    }

    private fun parseCase(obj: JSONObject): AblationTestCase {
        val imuSpec = obj.getJSONObject("imu")
        val tags = obj.getJSONArray("vision_only_tags")
        return AblationTestCase(
            testCaseId = obj.getString("test_case_id"),
            drillType = DrillType.valueOf(obj.getString("drill_type")),
            poses = createPoseSequence(
                totalFrames = obj.getInt("total_frames"),
                hipPeakFrame = obj.getInt("hip_peak_frame"),
                shoulderPeakFrame = obj.getInt("shoulder_peak_frame"),
                wristPeakFrame = obj.getInt("wrist_peak_frame"),
            ),
            imuSamples = createImuSequence(imuSpec),
            visionOnlyTags = (0 until tags.length()).map { tags.getString(it) },
            visionOnlyFeedback = obj.getString("vision_only_feedback"),
        )
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

    private fun createImuSequence(spec: JSONObject): List<ImuDataPoint> {
        val start = spec.getLong("start_ms")
        val duration = spec.getLong("duration_ms")
        val step = spec.getLong("step_ms")
        val racketTs = spec.getLong("racket_peak_ts")
        val impactTs = spec.getLong("impact_peak_ts")
        val impactGyroY = spec.getDouble("impact_gyro_y").toFloat()
        val samples = mutableListOf<ImuDataPoint>()
        var ts = start
        while (ts <= start + duration) {
            samples.add(
                ImuDataPoint(
                    timestampMs = ts,
                    accelX = if (ts == impactTs) 25f else 1f,
                    accelY = 0f,
                    accelZ = 0f,
                    gyroX = 0f,
                    gyroY = if (ts == impactTs) impactGyroY else 0f,
                    gyroZ = if (ts == racketTs) 1800f else 100f,
                ),
            )
            ts += step
        }
        return samples
    }
}
