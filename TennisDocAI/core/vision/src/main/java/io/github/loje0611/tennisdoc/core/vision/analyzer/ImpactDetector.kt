package io.github.loje0611.tennisdoc.core.vision.analyzer

import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class ImpactDetectionResult(
    val impactFrames: List<Int>,
    val velocities: List<Float>
)

object ImpactDetector {

    fun calculateVelocity(
        poseFrames: List<PoseFrame>,
        jointIndex: Int,
        fps: Float = 30f
    ): List<Float> {
        if (poseFrames.size < 2) {
            return emptyList()
        }

        val velocities = FloatArray(poseFrames.size)
        velocities[0] = 0.0f

        for (i in 1 until poseFrames.size) {
            val p1 = poseFrames[i - 1].landmarks.getOrNull(jointIndex)
            val p2 = poseFrames[i].landmarks.getOrNull(jointIndex)

            if (p1 == null || p2 == null || p1.isNan || p2.isNan) {
                velocities[i] = Float.NaN
            } else {
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dz = p2.z - p1.z
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                velocities[i] = dist * fps
            }
        }
        return velocities.toList()
    }

    fun gaussianFilter1d(
        data: FloatArray,
        sigma: Float = 2.0f
    ): FloatArray {
        val radius = ceil(4.0f * sigma).toInt()
        val kernelSize = 2 * radius + 1
        val kernel = FloatArray(kernelSize)
        var sum = 0.0f

        for (i in 0 until kernelSize) {
            val k = i - radius
            val v = exp(-(k * k) / (2f * sigma * sigma))
            kernel[i] = v
            sum += v
        }

        for (i in 0 until kernelSize) {
            kernel[i] /= sum
        }

        val result = FloatArray(data.size)
        for (i in data.indices) {
            var v = 0.0f
            for (j in 0 until kernelSize) {
                val k = j - radius
                var idx = i + k
                // Reflect mode for boundaries
                if (idx < 0) {
                    idx = -idx
                } else if (idx >= data.size) {
                    idx = 2 * data.size - 2 - idx
                }
                // Double check bounds after reflect (for very small arrays)
                if (idx < 0) idx = 0
                if (idx >= data.size) idx = data.size - 1

                v += data[idx] * kernel[j]
            }
            result[i] = v
        }
        return result
    }

    private data class Peak(val index: Int, val height: Float, val prominence: Float)

    private fun findPeaks(
        data: FloatArray,
        height: Float,
        distance: Int,
        prominence: Float
    ): List<Int> {
        val peaks = mutableListOf<Peak>()

        for (i in 1 until data.size - 1) {
            val v = data[i]
            if (v >= height && v > data[i - 1] && v > data[i + 1]) {
                // Calculate prominence
                var leftMin = v
                for (j in i - 1 downTo 0) {
                    if (data[j] > v) break
                    if (data[j] < leftMin) leftMin = data[j]
                }
                var rightMin = v
                for (j in i + 1 until data.size) {
                    if (data[j] > v) break
                    if (data[j] < rightMin) rightMin = data[j]
                }
                val p = v - max(leftMin, rightMin)
                if (p >= prominence) {
                    peaks.add(Peak(i, v, p))
                }
            }
        }

        // Apply distance constraint (highest prominence/height first)
        val sortedPeaks = peaks.sortedWith(compareByDescending<Peak> { it.prominence }.thenByDescending { it.height })
        val selectedPeaks = mutableListOf<Peak>()

        for (peak in sortedPeaks) {
            var conflict = false
            for (selected in selectedPeaks) {
                if (kotlin.math.abs(peak.index - selected.index) < distance) {
                    conflict = true
                    break
                }
            }
            if (!conflict) {
                selectedPeaks.add(peak)
            }
        }

        return selectedPeaks.map { it.index }.sorted()
    }

    fun detectImpactFrames(
        poseFrames: List<PoseFrame>,
        fps: Float = 30f,
        isRightHand: Boolean = true
    ): ImpactDetectionResult {
        val wristIndex = if (isRightHand) 16 else 15
        val velocities = calculateVelocity(poseFrames, wristIndex, fps)
        
        if (velocities.isEmpty()) {
            return ImpactDetectionResult(emptyList(), emptyList())
        }

        val velocitiesClean = velocities.map { if (it.isNaN()) 0f else it }.toFloatArray()
        val velocitiesSmooth = gaussianFilter1d(velocitiesClean, sigma = 2.0f)

        val maxVel = velocitiesSmooth.maxOrNull() ?: 0f
        if (maxVel == 0f) {
            return ImpactDetectionResult(listOf(0), velocities)
        }

        val minPeakHeight = maxVel * 0.5f
        val minProminence = maxVel * 0.3f
        val distanceFrames = (fps * 2.0f).toInt()

        val peaks = findPeaks(
            velocitiesSmooth,
            height = minPeakHeight,
            distance = distanceFrames,
            prominence = minProminence
        )

        if (peaks.isNotEmpty()) {
            return ImpactDetectionResult(peaks, velocities)
        } else {
            // Find max index in original clean velocities if no peak found
            var maxIdx = 0
            var maxVal = -1f
            for (i in velocitiesClean.indices) {
                if (velocitiesClean[i] > maxVal) {
                    maxVal = velocitiesClean[i]
                    maxIdx = i
                }
            }
            return ImpactDetectionResult(listOf(maxIdx), velocities)
        }
    }
}
