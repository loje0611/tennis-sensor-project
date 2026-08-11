package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.math.abs

class KineticChainAnalyzerTest {

    private fun loadFixture(): JSONArray {
        val uri = javaClass.classLoader?.getResource("golden_kinetic_chain_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_kinetic_chain_fixture.json")
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

    private fun assertClose(expected: Double, actual: Double, delta: Double = 1e-4) {
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
            
            val expectedPeakFrames = if (obj.isNull("expected_peak_frames")) null else obj.getJSONObject("expected_peak_frames")
            val expectedTimingMs = if (obj.isNull("expected_timing_ms")) null else obj.getJSONObject("expected_timing_ms")
            val expectedIsCorrectChain = if (obj.isNull("expected_is_correct_chain")) null else obj.getBoolean("expected_is_correct_chain")
            
            val result = KineticChainAnalyzer.analyzeKineticChain(poseFrames, fps = 30f, isRightHand = true)

            if (expectedPeakFrames == null) {
                assertNull("Test failed for $name: expected null but got result", result)
                continue
            }
            
            assertNotNull("Test failed for $name: expected result but got null", result)
            
            val resultPeakFrames = result!!.peakFrames
            assertEquals("Test failed for $name: hip peak mismatch", expectedPeakFrames.getInt("hip"), resultPeakFrames.hip)
            assertEquals("Test failed for $name: shoulder peak mismatch", expectedPeakFrames.getInt("shoulder"), resultPeakFrames.shoulder)
            assertEquals("Test failed for $name: wrist peak mismatch", expectedPeakFrames.getInt("wrist"), resultPeakFrames.wrist)
            
            val resultTimingMs = result.timingMs
            assertClose(expectedTimingMs!!.getDouble("hip_to_shoulder"), resultTimingMs.hipToShoulder)
            assertClose(expectedTimingMs.getDouble("shoulder_to_wrist"), resultTimingMs.shoulderToWrist)
            
            assertEquals("Test failed for $name: isCorrectChain mismatch", expectedIsCorrectChain, result.isCorrectChain)
        }
    }
}
