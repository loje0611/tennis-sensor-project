package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.abs

class SwingPathClassifierTest {

    private fun loadFixture(): JSONArray {
        val uri = javaClass.classLoader?.getResource("golden_swing_path_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_swing_path_fixture.json")
        val content = File(uri.toURI()).readText()
        return JSONArray(content)
    }

    private fun parseLandmark(array: JSONArray): PoseLandmark {
        val x = if (array.get(0).toString() == "NaN") Float.NaN else array.getDouble(0).toFloat()
        val y = if (array.get(1).toString() == "NaN") Float.NaN else array.getDouble(1).toFloat()
        val z = if (array.get(2).toString() == "NaN") Float.NaN else array.getDouble(2).toFloat()
        val visibility = if (array.length() > 3) {
            if (array.get(3).toString() == "NaN") Float.NaN else array.getDouble(3).toFloat()
        } else 1.0f
        return PoseLandmark(x, y, z, visibility)
    }

    private fun parsePoseData(poseArr: JSONArray): List<PoseFrame> {
        val poseFrames = mutableListOf<PoseFrame>()
        for (i in 0 until poseArr.length()) {
            val frameArr = poseArr.getJSONArray(i)
            val landmarks = mutableListOf<PoseLandmark>()
            for (j in 0 until frameArr.length()) {
                landmarks.add(parseLandmark(frameArr.getJSONArray(j)))
            }
            poseFrames.add(PoseFrame(landmarks))
        }
        return poseFrames
    }

    private fun getExpectedImpactFrame(obj: JSONObject): Int? {
        if (obj.isNull("impact_frame")) return null
        return obj.getInt("impact_frame")
    }

    private fun assertClose(expected: Double, actual: Double, delta: Double = 1e-5) {
        if (expected.isNaN()) {
            assertEquals("Expected NaN but got $actual", true, actual.isNaN())
        } else {
            assertEquals(expected, actual, delta)
        }
    }

    @Test
    fun testGoldenFixtures() {
        val fixtures = loadFixture()

        for (i in 0 until fixtures.length()) {
            val obj = fixtures.getJSONObject(i)
            val name = obj.getString("name")

            val poseFrames = parsePoseData(obj.getJSONArray("pose"))
            val impactFrame = getExpectedImpactFrame(obj)
            val expectedClass = obj.getString("expected_class")
            
            // To test slope calculation correctly, we can extract the trajectory similar to classifySwingPath
            val analysisWindow = 10
            val wristIndex = 16
            val yTrajectory = mutableListOf<Double>()
            if (impactFrame != null && poseFrames.isNotEmpty()) {
                val startFrame = maxOf(0, impactFrame - analysisWindow)
                val endFrame = minOf(poseFrames.size, impactFrame + analysisWindow)
                for (j in startFrame until endFrame) {
                    val landmark = poseFrames[j].landmarks.getOrNull(wristIndex)
                    if (landmark != null && !landmark.isNan) {
                        yTrajectory.add(landmark.y.toDouble())
                    }
                }
            }

            val expectedSlopeRaw = obj.get("expected_slope")
            val expectedSlope = if (expectedSlopeRaw.toString() == "NaN") Double.NaN else obj.getDouble("expected_slope")
            val slope = if (yTrajectory.size >= 2) SwingPathClassifier.calculateLinearSlope(yTrajectory.toDoubleArray()) else Double.NaN
            
            assertClose(expectedSlope, slope)

            val resultClass = SwingPathClassifier.classifySwingPath(poseFrames, impactFrame, isRightHand = true, analysisWindow = 10)
            assertEquals("Test failed for $name: class mismatch", expectedClass, resultClass.key)
        }
    }
}
