package io.github.loje0611.tennisdoc.lab

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.loje0611.tennisdoc.feature.lab.pipeline.SwingVideoEncoderImpl
import io.github.loje0611.tennisdoc.feature.lab.pipeline.SwingVideoFrame
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwingVideoEncoderInstrumentedTest {

    @Test
    fun encodeToMp4WritesFtypMp4File() = runBlocking {
        val frames = (0 until 8).map { i ->
            val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if (i % 2 == 0) Color.GREEN else Color.BLUE)
            SwingVideoFrame(i * 33L, bitmap)
        }
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "task052-swing-clip.mp4",
        )
        output.delete()
        try {
            val success = SwingVideoEncoderImpl().encodeToMp4(
                frames = frames,
                outputFile = output,
                width = 480,
                height = 640,
                fps = 30,
            )
            assertTrue("MediaCodec encode must succeed on device", success)
            assertTrue(output.exists())
            assertTrue(output.length() > 32L)
            val header = ByteArray(64)
            output.inputStream().use { stream ->
                val read = stream.read(header)
                assertTrue(read > 8)
            }
            val ascii = header.toString(Charsets.ISO_8859_1)
            assertTrue("MP4 header must contain ftyp, was: $ascii", ascii.contains("ftyp"))
        } finally {
            frames.forEach { it.bitmap.recycle() }
            output.delete()
        }
    }
}
