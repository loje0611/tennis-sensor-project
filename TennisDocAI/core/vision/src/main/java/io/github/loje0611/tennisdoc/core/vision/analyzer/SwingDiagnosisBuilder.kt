package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.FeedbackItem
import io.github.loje0611.tennisdoc.core.vision.model.JointVelocities
import io.github.loje0611.tennisdoc.core.vision.model.SwingDiagnosisResult
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object SwingDiagnosisBuilder {

    fun buildSwingFeedbacks(
        impactFrames: List<Int>,
        swingTypes: List<String>,
        armAngles: List<Double>,
        chainVelocities: JointVelocities?,
        fps: Float = 30f
    ): SwingDiagnosisResult {
        val swingFeedbacks = mutableMapOf<Int, List<FeedbackItem>>()
        val allProblems = mutableListOf<String>()

        for ((i, frame) in impactFrames.withIndex()) {
            val stype = swingTypes.getOrElse(i) { "Unknown" }
            val armAngle = if (frame in armAngles.indices) armAngles[frame] else 0.0
            val feedbacks = mutableListOf<FeedbackItem>()

            if (chainVelocities != null) {
                val startF = max(0, (frame - fps * 1.0f).toInt())
                val endF = min(chainVelocities.hip.size, (frame + fps * 0.5f).toInt())
                
                if (startF < endF) {
                    val hipSlice = chainVelocities.hip.subList(startF, endF)
                    val shoulderSlice = chainVelocities.shoulder.subList(startF, endF)
                    val wristSlice = chainVelocities.wrist.subList(startF, endF)

                    val hipMax = hipSlice.maxOrNull()
                    val hipMaxIdx = if (hipMax != null) hipSlice.indexOf(hipMax).takeIf { it >= 0 } ?: 0 else 0
                    val shoulderMax = shoulderSlice.maxOrNull()
                    val shoulderMaxIdx = if (shoulderMax != null) shoulderSlice.indexOf(shoulderMax).takeIf { it >= 0 } ?: 0 else 0
                    val wristMax = wristSlice.maxOrNull()
                    val wristMaxIdx = if (wristMax != null) wristSlice.indexOf(wristMax).takeIf { it >= 0 } ?: 0 else 0

                    val peakHip = startF + hipMaxIdx
                    val peakShoulder = startF + shoulderMaxIdx
                    val peakWrist = startF + wristMaxIdx

                    if (peakHip >= peakShoulder) {
                        feedbacks.add(FeedbackItem("Use Hip First", 24))
                        allProblems.add("운동 체인(하체->상체 순서)")
                    } else if (peakShoulder >= peakWrist) {
                        feedbacks.add(FeedbackItem("Late Wrist", 16))
                        allProblems.add("팔/손목 가속")
                    }
                }
            }

            if (armAngle < 120.0) {
                val angleStr = String.format(Locale.US, "%.0f", armAngle)
                feedbacks.add(FeedbackItem("Arm Bent($angleStr)", 14))
                allProblems.add("타점(팔 각도)")
            }

            if (stype == "Flat" || stype == "Slice") {
                feedbacks.add(FeedbackItem("Low Path", 16))
                allProblems.add("상향 스윙 궤적")
            }

            if (feedbacks.isEmpty()) {
                feedbacks.add(FeedbackItem("Good Swing!", 12))
            }

            swingFeedbacks[frame] = feedbacks
        }

        return SwingDiagnosisResult(
            swingFeedbacks = swingFeedbacks,
            allProblems = allProblems
        )
    }
}
