package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.feature.lab.landmarker.PoseLandmarkerWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PoseAnalysisAnalyzerTest {

    private fun createMockImageProxy(
        shouldThrowOnBitmap: Boolean,
        rotationDegrees: Int = 0,
        bitmapWidth: Int = 640,
        bitmapHeight: Int = 480,
        onClose: () -> Unit,
    ): ImageProxy {
        val imageInfo = Proxy.newProxyInstance(
            ImageInfo::class.java.classLoader,
            arrayOf(ImageInfo::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getTimestamp" -> 123456789L
                "getRotationDegrees" -> rotationDegrees
                else -> null
            }
        } as ImageInfo

        return Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "close" -> {
                    onClose()
                    null
                }
                "getImageInfo" -> imageInfo
                "toBitmap" -> {
                    if (shouldThrowOnBitmap) {
                        throw RuntimeException("Simulated bitmap failure")
                    }
                    Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                }
                "hashCode" -> 1
                "equals" -> false
                "toString" -> "MockImageProxy"
                else -> null
            }
        } as ImageProxy
    }

    private class StubLandmarkerWrapper(
        val returnFrame: PoseFrame?,
        val throwOnProcess: Boolean = false,
    ) : PoseLandmarkerWrapper {
        var processImageCalled = false
        var lastFrameIndex: Long? = null
        var lastTimestampMs: Long? = null
        var lastBitmapWidth: Int = -1
        var lastBitmapHeight: Int = -1
        override val isInitialized: Boolean = true
        override fun processImage(bitmap: Bitmap, frameIndex: Long, timestampMs: Long): PoseFrame? {
            processImageCalled = true
            lastFrameIndex = frameIndex
            lastTimestampMs = timestampMs
            lastBitmapWidth = bitmap.width
            lastBitmapHeight = bitmap.height
            if (throwOnProcess) throw RuntimeException("landmarker failure")
            return returnFrame
        }
        override fun close() {}
    }

    @Test
    fun testAnalyze_callsCloseAndCallback_success() {
        var callbackCalled = false
        var closeCalled = false
        val expectedFrame = PoseFrame(emptyList())
        val stubWrapper = StubLandmarkerWrapper(expectedFrame)
        val analyzer = PoseAnalysisAnalyzer(stubWrapper) { frame ->
            callbackCalled = true
            assertEquals(expectedFrame, frame)
        }

        val imageProxy = createMockImageProxy(shouldThrowOnBitmap = false) {
            closeCalled = true
        }
        analyzer.analyze(imageProxy)

        assertTrue("Callback should be called", callbackCalled)
        assertTrue("Wrapper should be called", stubWrapper.processImageCalled)
        assertEquals(0L, stubWrapper.lastFrameIndex)
        assertEquals(123L, stubWrapper.lastTimestampMs)
        assertTrue("ImageProxy.close() must be called", closeCalled)
    }

    @Test
    fun testAnalyze_callsClose_whenBitmapFails() {
        var callbackCalled = false
        var closeCalled = false
        val stubWrapper = StubLandmarkerWrapper(null)
        val analyzer = PoseAnalysisAnalyzer(stubWrapper) { frame ->
            callbackCalled = true
            assertEquals(null, frame)
        }

        val imageProxy = createMockImageProxy(shouldThrowOnBitmap = true) {
            closeCalled = true
        }
        analyzer.analyze(imageProxy)

        assertTrue("Callback should be called even if bitmap fails", callbackCalled)
        assertTrue("Wrapper must not run when bitmap conversion fails", !stubWrapper.processImageCalled)
        assertTrue("ImageProxy.close() must be called", closeCalled)
    }

    @Test
    fun testAnalyze_callsClose_whenProcessImageThrows() {
        var closeCalled = false
        val stubWrapper = StubLandmarkerWrapper(null, throwOnProcess = true)
        val analyzer = PoseAnalysisAnalyzer(stubWrapper) { }

        val imageProxy = createMockImageProxy(shouldThrowOnBitmap = false) {
            closeCalled = true
        }
        try {
            analyzer.analyze(imageProxy)
        } catch (_: RuntimeException) {
            // processImage failure may propagate; close() must still run
        }
        assertTrue("ImageProxy.close() must be called when processImage throws", closeCalled)
    }

    @Test
    fun analyze_rotatesBitmap90DegreesBeforeLandmarker() {
        val stubWrapper = StubLandmarkerWrapper(PoseFrame(emptyList()))
        val analyzer = PoseAnalysisAnalyzer(stubWrapper) { }

        analyzer.analyze(
            createMockImageProxy(
                shouldThrowOnBitmap = false,
                rotationDegrees = 90,
                bitmapWidth = 640,
                bitmapHeight = 480,
                onClose = {},
            ),
        )

        assertTrue(stubWrapper.processImageCalled)
        assertEquals(480, stubWrapper.lastBitmapWidth)
        assertEquals(640, stubWrapper.lastBitmapHeight)
    }

    @Test
    fun analyze_keepsBitmapSizeWhenRotationIsZero() {
        val stubWrapper = StubLandmarkerWrapper(PoseFrame(emptyList()))
        val analyzer = PoseAnalysisAnalyzer(stubWrapper) { }

        analyzer.analyze(
            createMockImageProxy(
                shouldThrowOnBitmap = false,
                rotationDegrees = 0,
                bitmapWidth = 640,
                bitmapHeight = 480,
                onClose = {},
            ),
        )

        assertEquals(640, stubWrapper.lastBitmapWidth)
        assertEquals(480, stubWrapper.lastBitmapHeight)
    }
}
