package io.github.loje0611.tennisdoc.core.fusion.coaching

import io.github.loje0611.tennisdoc.core.fusion.model.KineticChain5Stage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStage
import io.github.loje0611.tennisdoc.core.fusion.model.KineticStageType
import io.github.loje0611.tennisdoc.core.fusion.model.RacketFaceState
import io.github.loje0611.tennisdoc.core.fusion.model.RacketImpactOrientation
import io.github.loje0611.tennisdoc.core.fusion.model.SyncAnchor
import io.github.loje0611.tennisdoc.core.model.DrillType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CausalCoachingEngineGoldenFixtureTest {

    private val engine = CausalCoachingEngine()

    @Test
    fun `golden JSON fixtures all pass`() {
        val fixtures = loadFixtures()
        assertTrue("golden fixture file must not be empty", fixtures.length() > 0)

        for (i in 0 until fixtures.length()) {
            val case = fixtures.getJSONObject(i)
            val name = case.getString("name")
            val first = diagnose(case)
            val second = diagnose(case)

            assertEquals("$name deterministic tags", first.diagnosisTags, second.diagnosisTags)
            assertEquals("$name deterministic cause", first.primaryCause, second.primaryCause)

            val expected = case.getJSONObject("expected")
            val tags = expected.getJSONArray("tags")
            for (t in 0 until tags.length()) {
                val tag = tags.getString(t)
                assertTrue(
                    "$name missing tag $tag in ${first.diagnosisTags}",
                    first.diagnosisTags.contains(tag),
                )
            }
            assertEquals("$name primaryCause", expected.getString("primary_cause"), first.primaryCause)
            if (expected.has("feedback_contains")) {
                val needle = expected.getString("feedback_contains")
                assertTrue(
                    "$name feedback missing '$needle': ${first.coachingFeedback}",
                    first.coachingFeedback.contains(needle),
                )
            }
            if (expected.has("explanation_contains")) {
                val needle = expected.getString("explanation_contains")
                assertTrue(
                    "$name explanation missing '$needle': ${first.causalExplanation}",
                    first.causalExplanation.contains(needle),
                )
            }
        }
    }

    private fun diagnose(case: JSONObject) = engine.diagnose(
        drillType = DrillType.valueOf(case.getString("drill_type")),
        anchor = parseAnchor(case.getJSONObject("anchor")),
        kineticChain = parseChain(case.getJSONObject("chain")),
        racketImpact = parseRacket(case.getJSONObject("racket_impact")),
        poses = emptyList(),
        imuSamples = emptyList(),
    )

    private fun loadFixtures(): JSONArray {
        val uri = javaClass.classLoader?.getResource("golden_causal_coaching_fixture.json")
            ?: throw IllegalStateException("Cannot find golden_causal_coaching_fixture.json")
        return JSONArray(File(uri.toURI()).readText())
    }

    private fun parseAnchor(obj: JSONObject) = SyncAnchor(
        visionImpactTimestampMs = obj.getLong("vision_impact_timestamp_ms"),
        sensorImpactTimestampMs = obj.getLong("sensor_impact_timestamp_ms"),
        timeOffsetMs = obj.getLong("time_offset_ms"),
        confidence = obj.getDouble("confidence").toFloat(),
        isSynchronized = obj.getBoolean("is_synchronized"),
    )

    private fun parseChain(obj: JSONObject): KineticChain5Stage {
        val stagesArr = obj.getJSONArray("stages")
        val stages = (0 until stagesArr.length()).map { i ->
            val s = stagesArr.getJSONObject(i)
            KineticStage(
                stage = KineticStageType.valueOf(s.getString("stage")),
                peakTimestampMs = s.getLong("peak_timestamp_ms"),
                peakValue = s.getDouble("peak_value").toFloat(),
                delayFromPreviousMs = s.optLong("delay_from_previous_ms", 0L),
            )
        }
        return KineticChain5Stage(
            stages = stages,
            isSequential = obj.getBoolean("is_sequential"),
            totalDurationMs = obj.getLong("total_duration_ms"),
            energyTransferEfficiency = obj.getDouble("energy_transfer_efficiency").toFloat(),
        )
    }

    private fun parseRacket(obj: JSONObject) = RacketImpactOrientation(
        rollDeg = obj.getDouble("roll_deg").toFloat(),
        pitchDeg = obj.getDouble("pitch_deg").toFloat(),
        yawDeg = obj.getDouble("yaw_deg").toFloat(),
        faceState = RacketFaceState.valueOf(obj.getString("face_state")),
        deviationDeg = obj.getDouble("deviation_deg").toFloat(),
    )
}
