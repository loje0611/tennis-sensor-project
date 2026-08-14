package io.github.loje0611.tennisdoc.lab

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.feature.lab.landmarker.MediaPipePoseLandmarkerWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPipePoseLandmarkerInstrumentedTest {

    @Test
    fun realWrapper_initializesNativeSdkAndProcessesBitmap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wrapper = MediaPipePoseLandmarkerWrapper(context)
        try {
            assertTrue("PoseLandmarker must initialize on device", wrapper.isInitialized)

            val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.GRAY)
            val frame = wrapper.processImage(bitmap, frameIndex = 0L, timestampMs = 1L)
            assertNotNull("detect() must return a PoseFrame (empty list if no person)", frame)
            if (frame!!.landmarks.isNotEmpty()) {
                assertEquals(33, frame.landmarks.size)
                frame.landmarks.forEach { landmark ->
                    assertTrue(landmark.x in -0.5f..1.5f)
                    assertTrue(landmark.y in -0.5f..1.5f)
                }
            }

            val recycled = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            recycled.recycle()
            assertNull(wrapper.processImage(recycled, 1L, 2L))
        } finally {
            wrapper.close()
            wrapper.close()
            assertFalse(wrapper.isInitialized)
        }
    }
}
