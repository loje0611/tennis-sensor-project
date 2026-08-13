package io.github.loje0611.tennisdoc.feature.lab.landmarker

import android.graphics.Bitmap
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark

class FakePoseLandmarkerWrapper : PoseLandmarkerWrapper {
    override var isInitialized: Boolean = true
    var isClosed: Boolean = false
    
    override fun processImage(
        bitmap: Bitmap,
        frameIndex: Long,
        timestampMs: Long
    ): PoseFrame? {
        if (!isInitialized || isClosed || bitmap.isRecycled) return null
        val fakeLandmarks = List(33) { 
            PoseLandmark(it.toFloat(), 0f, 0f, 1f) 
        }
        return PoseFrame(fakeLandmarks)
    }

    override fun close() {
        isClosed = true
        isInitialized = false
    }
}
