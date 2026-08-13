package io.github.loje0611.tennisdoc.feature.lab.landmarker

import android.graphics.Bitmap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PoseLandmarkerWrapperTest {

    private lateinit var wrapper: FakePoseLandmarkerWrapper

    @Before
    fun setup() {
        wrapper = FakePoseLandmarkerWrapper()
    }

    @After
    fun teardown() {
        wrapper.close()
    }

    @Test
    fun testInitializationAndClose() {
        assertTrue(wrapper.isInitialized)
        wrapper.close()
        assertFalse(wrapper.isInitialized)
        assertTrue(wrapper.isClosed)

        wrapper.close()
        wrapper.close()
        assertFalse(wrapper.isInitialized)
        assertTrue(wrapper.isClosed)
    }

    @Test
    fun processImageAfterCloseReturnsNull() {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        wrapper.close()
        assertNull(wrapper.processImage(bitmap, 1L, 2L))
    }

    @Test
    fun testProcessImage_success() {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val frame = wrapper.processImage(bitmap, 0L, 0L)

        assertNotNull(frame)
        assertEquals(33, frame!!.landmarks.size)

        for (i in 0..32) {
            assertEquals(i.toFloat(), frame.landmarks[i].x, 0.001f)
            assertEquals(0f, frame.landmarks[i].y, 0.001f)
            assertEquals(0f, frame.landmarks[i].z, 0.001f)
            assertEquals(1.0f, frame.landmarks[i].visibility, 0.001f)
        }
    }

    @Test
    fun testProcessImage_recycledBitmap() {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        bitmap.recycle()

        val frame = wrapper.processImage(bitmap, 0L, 0L)
        assertNull(frame)
    }

    @Test
    fun realWrapperInitFailureIsIllegalStateExceptionNotLinkError() {
        val context = RuntimeEnvironment.getApplication()
        try {
            MediaPipePoseLandmarkerWrapper(context).close()
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
            return
        } catch (e: UnsatisfiedLinkError) {
            fail("UnsatisfiedLinkError escaped wrapper init; FR-2 / JVM EH require IllegalStateException")
        }
    }

    @Test
    fun testMapLandmarksToPoseFrame() {
        val emptyFrame = MediaPipePoseLandmarkerWrapper.mapLandmarksToPoseFrame(emptyList())
        assertNotNull(emptyFrame)
        assertTrue(emptyFrame.landmarks.isEmpty())

        val mockLandmarks = List(33) { i ->
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark.create(
                i.toFloat(),
                i * 0.01f,
                i * -0.02f,
                java.util.Optional.of(0.5f),
                java.util.Optional.empty(),
            )
        }

        val frame = MediaPipePoseLandmarkerWrapper.mapLandmarksToPoseFrame(listOf(mockLandmarks))
        assertEquals(33, frame.landmarks.size)
        for (i in 0..32) {
            assertEquals(i.toFloat(), frame.landmarks[i].x, 0.001f)
            assertEquals(i * 0.01f, frame.landmarks[i].y, 0.001f)
            assertEquals(i * -0.02f, frame.landmarks[i].z, 0.001f)
            assertEquals(0.5f, frame.landmarks[i].visibility, 0.001f)
        }
    }

    @Test
    fun mapLandmarksDefaultsVisibilityWhenAbsent() {
        val landmark = com.google.mediapipe.tasks.components.containers.NormalizedLandmark.create(
            0.1f,
            0.2f,
            0.3f,
            java.util.Optional.empty(),
            java.util.Optional.empty(),
        )
        val frame = MediaPipePoseLandmarkerWrapper.mapLandmarksToPoseFrame(listOf(listOf(landmark)))
        assertEquals(1, frame.landmarks.size)
        assertEquals(0.1f, frame.landmarks[0].x, 0.001f)
        assertEquals(0.2f, frame.landmarks[0].y, 0.001f)
        assertEquals(0.3f, frame.landmarks[0].z, 0.001f)
        assertEquals(1.0f, frame.landmarks[0].visibility, 0.001f)
    }
}
