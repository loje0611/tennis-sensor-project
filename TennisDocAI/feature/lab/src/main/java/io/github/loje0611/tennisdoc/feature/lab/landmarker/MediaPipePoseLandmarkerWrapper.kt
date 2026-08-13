package io.github.loje0611.tennisdoc.feature.lab.landmarker

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.core.vision.model.PoseLandmark

class MediaPipePoseLandmarkerWrapper(context: Context) : PoseLandmarkerWrapper {
    private var poseLandmarker: PoseLandmarker? = null
    
    override val isInitialized: Boolean
        get() = poseLandmarker != null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .setDelegate(Delegate.CPU)
                .build()

            val options = PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
                ?: throw IllegalStateException("Failed to create PoseLandmarker")
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to initialize PoseLandmarker, possibly missing model file.", e)
        }
    }

    override fun processImage(
        bitmap: Bitmap,
        frameIndex: Long,
        timestampMs: Long
    ): PoseFrame? {
        if (!isInitialized) return null
        if (bitmap.isRecycled) {
            return null // AC-7 requirement: return null for invalid/recycled bitmap
        }

        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = poseLandmarker?.detect(mpImage) ?: return null
        return mapLandmarksToPoseFrame(result.landmarks())
    }

    override fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    companion object {
        fun mapLandmarksToPoseFrame(poses: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>): PoseFrame {
            if (poses.isEmpty()) {
                return PoseFrame(emptyList())
            }

            // AC-4: Ensure index mapping 0-32 is 1:1. MediaPipe already provides 33 landmarks in order.
            val firstPose = poses[0]
            val landmarks = firstPose.map { mpLandmark ->
                PoseLandmark(
                    x = mpLandmark.x(),
                    y = mpLandmark.y(),
                    z = mpLandmark.z(),
                    visibility = mpLandmark.visibility().orElse(1.0f)
                )
            }
            
            return PoseFrame(landmarks)
        }
    }
}
