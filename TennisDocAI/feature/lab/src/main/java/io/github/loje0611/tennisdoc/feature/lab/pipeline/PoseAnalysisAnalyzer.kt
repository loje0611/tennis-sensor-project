package io.github.loje0611.tennisdoc.feature.lab.pipeline

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.feature.lab.landmarker.PoseLandmarkerWrapper

class PoseAnalysisAnalyzer(
    private val landmarkerWrapper: PoseLandmarkerWrapper,
    private val onPoseExtracted: (PoseFrame?) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var sequenceNumber = 0L

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val bitmap = try {
                imageProxy.toBitmap()
            } catch (e: Exception) {
                null
            }
            if (bitmap != null) {
                val poseFrame = landmarkerWrapper.processImage(
                    bitmap = bitmap,
                    frameIndex = sequenceNumber++,
                    timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
                )
                onPoseExtracted(poseFrame)
            } else {
                onPoseExtracted(null)
            }
        } finally {
            imageProxy.close()
        }
    }
}
