package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.FeedbackItem
import io.github.loje0611.tennisdoc.core.vision.model.JointVelocities
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SwingDiagnosisBuilderTest {

    private fun loadFixture(): JSONArray {
        val uri = javaClass.classLoader?.getResource("golden_swing_diagnosis_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_swing_diagnosis_fixture.json")
        val content = File(uri.toURI()).readText()
        return JSONArray(content)
    }

    private fun toIntList(jsonArray: JSONArray): List<Int> {
        val list = mutableListOf<Int>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getInt(i))
        }
        return list
    }

    private fun toStringList(jsonArray: JSONArray): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    private fun toDoubleList(jsonArray: JSONArray): List<Double> {
        val list = mutableListOf<Double>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getDouble(i))
        }
        return list
    }

    private fun toFloatList(jsonArray: JSONArray): List<Float> {
        val list = mutableListOf<Float>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getDouble(i).toFloat())
        }
        return list
    }

    @Test
    fun testGoldenFixtures() {
        val fixtures = loadFixture()

        for (i in 0 until fixtures.length()) {
            val obj = fixtures.getJSONObject(i)
            val name = obj.getString("name")

            val impactFrames = toIntList(obj.getJSONArray("impact_frames"))
            val swingTypes = toStringList(obj.getJSONArray("swing_types"))
            val armAngles = toDoubleList(obj.getJSONArray("arm_angles"))
            val fps = obj.getDouble("fps").toFloat()

            val chainVelocitiesJson = if (obj.isNull("chain_velocities")) null else obj.getJSONObject("chain_velocities")
            val chainVelocities = chainVelocitiesJson?.let {
                JointVelocities(
                    hip = toFloatList(it.getJSONArray("hip")),
                    shoulder = toFloatList(it.getJSONArray("shoulder")),
                    wrist = toFloatList(it.getJSONArray("wrist"))
                )
            }

            val expectedSwingFeedbacksJson = obj.getJSONObject("expected_swing_feedbacks")
            val expectedAllProblems = toStringList(obj.getJSONArray("expected_all_problems"))

            val result = SwingDiagnosisBuilder.buildSwingFeedbacks(
                impactFrames, swingTypes, armAngles, chainVelocities, fps
            )

            assertEquals("Test failed for $name: all_problems mismatch", expectedAllProblems, result.allProblems)

            assertEquals("Test failed for $name: swingFeedbacks size mismatch", expectedSwingFeedbacksJson.length(), result.swingFeedbacks.size)
            
            for (key in expectedSwingFeedbacksJson.keys()) {
                val expectedFeedbacksJson = expectedSwingFeedbacksJson.getJSONArray(key)
                val frame = key.toInt()
                val actualFeedbacks = result.swingFeedbacks[frame] ?: emptyList()

                assertEquals("Test failed for $name: feedback list size mismatch for frame $frame", expectedFeedbacksJson.length(), actualFeedbacks.size)
                
                for (j in 0 until expectedFeedbacksJson.length()) {
                    val expectedFeedbackObj = expectedFeedbacksJson.getJSONObject(j)
                    val expectedText = expectedFeedbackObj.getString("text")
                    val expectedTargetJoint = expectedFeedbackObj.getInt("target_joint")

                    val actualFeedback = actualFeedbacks[j]
                    assertEquals("Test failed for $name: feedback text mismatch for frame $frame", expectedText, actualFeedback.text)
                    assertEquals("Test failed for $name: feedback target_joint mismatch for frame $frame", expectedTargetJoint, actualFeedback.targetJoint)
                }
            }
        }
    }
}
