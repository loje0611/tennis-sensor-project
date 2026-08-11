package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.abs

class ImpactDetectorTest {

    private fun loadFixture(): JSONArray {
        val uri = javaClass.classLoader?.getResource("golden_impact_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_impact_fixture.json")
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

    private fun parseFloatList(arr: JSONArray): List<Float> {
        val list = mutableListOf<Float>()
        for (i in 0 until arr.length()) {
            val value = arr.get(i)
            if (value.toString() == "NaN") {
                list.add(Float.NaN)
            } else {
                list.add(arr.getDouble(i).toFloat())
            }
        }
        return list
    }
    
    private fun parseIntList(arr: JSONArray): List<Int> {
        val list = mutableListOf<Int>()
        for (i in 0 until arr.length()) {
            list.add(arr.getInt(i))
        }
        return list
    }

    private fun assertClose(expected: Float, actual: Float, delta: Float = 1e-4f) {
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
            val expectedImpactFrames = parseIntList(obj.getJSONArray("expected_impact_frames"))
            val expectedVelocities = parseFloatList(obj.getJSONArray("expected_velocities"))

            val result = ImpactDetector.detectImpactFrames(poseFrames, fps = 30f, isRightHand = true)

            assertEquals("Test failed for $name: impactFrames size mismatch", expectedImpactFrames.size, result.impactFrames.size)
            for (j in expectedImpactFrames.indices) {
                assertEquals("Test failed for $name: impactFrame at $j mismatch", expectedImpactFrames[j], result.impactFrames[j])
            }

            assertEquals("Test failed for $name: velocities size mismatch", expectedVelocities.size, result.velocities.size)
            for (j in expectedVelocities.indices) {
                assertClose(expectedVelocities[j], result.velocities[j])
            }
        }
    }
}
