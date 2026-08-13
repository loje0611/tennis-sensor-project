package io.github.loje0611.tennisdoc.feature.lab.landmarker

import android.graphics.Bitmap
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame

interface PoseLandmarkerWrapper : AutoCloseable {
    val isInitialized: Boolean
    
    fun processImage(
        bitmap: Bitmap,
        frameIndex: Long,
        timestampMs: Long
    ): PoseFrame?
}
