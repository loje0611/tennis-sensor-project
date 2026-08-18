package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SwingVideoEncoderTest {

    @Test
    fun emptyFramesReturnsFalseWithoutCreatingOutput() = runTest {
        val encoder = SwingVideoEncoderImpl()
        val output = File.createTempFile("empty_video", ".mp4")
        output.delete()
        try {
            val result = encoder.encodeToMp4(emptyList(), output)
            assertFalse(result)
            assertFalse(output.exists())
        } finally {
            output.delete()
        }
    }

    @Test
    fun encodeFailSafeDoesNotThrow() = runTest {
        val encoder = SwingVideoEncoderImpl()
        val frames = listOf(
            SwingVideoFrame(0L, Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)),
            SwingVideoFrame(33L, Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)),
        )
        val output = File.createTempFile("swing_clip", ".mp4")
        output.delete()
        try {
            val result = encoder.encodeToMp4(frames, output, width = 480, height = 640, fps = 30)
            if (result) {
                assertTrue(output.exists())
                assertTrue(output.length() > 0L)
                assertTrue("MP4 must contain ftyp box", output.readBytes().toString(Charsets.ISO_8859_1).contains("ftyp"))
            } else {
                assertFalse(output.exists())
            }
        } finally {
            frames.forEach { it.bitmap.recycle() }
            output.delete()
        }
    }
}
