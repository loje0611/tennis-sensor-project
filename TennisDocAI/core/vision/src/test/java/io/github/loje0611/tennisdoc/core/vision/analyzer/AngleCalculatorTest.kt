package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class AngleCalculatorTest {

    private fun loadFixture(): JSONArray {
        val uri = javaClass.classLoader?.getResource("golden_angles_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_angles_fixture.json")
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

    private fun assertClose(expected: Double, actual: Double, delta: Double = 1e-5) {
        if (expected.isNaN()) {
            assertTrue("Expected NaN but got $actual", actual.isNaN())
        } else {
            assertEquals(expected, actual, delta)
        }
    }

    private fun getExpectedValue(obj: JSONObject, key: String): Double {
        val value = obj.get(key)
        if (value.toString() == "NaN") {
            return Double.NaN
        }
        return obj.getDouble(key)
    }

    @Test
    fun testGoldenFixtures() {
        val fixtures = loadFixture()

        for (i in 0 until fixtures.length()) {
            val obj = fixtures.getJSONObject(i)
            val name = obj.getString("name")

            if (obj.has("pose")) {
                val poseArr = obj.getJSONArray("pose")
                val landmarks = mutableListOf<PoseLandmark>()
                for (j in 0 until poseArr.length()) {
                    landmarks.add(parseLandmark(poseArr.getJSONArray(j)))
                }
                val frame = PoseFrame(landmarks)
                val result = AngleCalculator.getJointAnglesFromPose(frame)

                val expectedRightArm = getExpectedValue(obj, "expected_right_arm")
                val expectedRightKnee = getExpectedValue(obj, "expected_right_knee")

                assertClose(expectedRightArm, result.rightArmAngle)
                assertClose(expectedRightKnee, result.rightKneeAngle)
            } else {
                val a = parseLandmark(obj.getJSONArray("a"))
                val b = parseLandmark(obj.getJSONArray("b"))
                val c = parseLandmark(obj.getJSONArray("c"))
                val expected = getExpectedValue(obj, "expected")

                val result = AngleCalculator.calculate3dAngle(a, b, c)
                assertClose(expected, result)
            }
        }
    }
}
