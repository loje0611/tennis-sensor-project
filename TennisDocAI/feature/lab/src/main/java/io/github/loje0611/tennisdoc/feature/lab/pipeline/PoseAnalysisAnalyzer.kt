package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap
import android.graphics.Matrix
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
        var processedBitmap: Bitmap? = null
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rawBitmap = try {
                imageProxy.toBitmap()
            } catch (e: Exception) {
                null
            }
            if (rawBitmap != null) {
                processedBitmap = if (rotationDegrees != 0) {
                    val matrix = Matrix().apply {
                        postRotate(rotationDegrees.toFloat())
                    }
                    val rotated = Bitmap.createBitmap(
                        rawBitmap,
                        0,
                        0,
                        rawBitmap.width,
                        rawBitmap.height,
                        matrix,
                        true
                    )
                    if (rotated != rawBitmap) {
                        rawBitmap.recycle()
                    }
                    rotated
                } else {
                    rawBitmap
                }

                val poseFrame = landmarkerWrapper.processImage(
                    bitmap = processedBitmap,
                    frameIndex = sequenceNumber++,
                    timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
                )
                onPoseExtracted(poseFrame)
            } else {
                onPoseExtracted(null)
            }
        } finally {
            processedBitmap?.recycle()
            imageProxy.close()
        }
    }
}
