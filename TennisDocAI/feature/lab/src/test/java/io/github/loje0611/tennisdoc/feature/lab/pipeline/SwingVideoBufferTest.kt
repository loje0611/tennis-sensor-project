package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SwingVideoBufferTest {

    @Test
    fun addFramePrunesOlderThanThreeSecondWindow() {
        val buffer = SwingVideoBuffer(bufferDurationMs = 3000L)
        val stamps = listOf(0L, 500L, 1500L, 2500L, 3500L, 4500L)
        stamps.forEach { ts ->
            buffer.addFrame(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), ts)
        }

        val snapshot = buffer.snapshot()
        val kept = snapshot.map { it.timestampMs }
        assertEquals(listOf(1500L, 2500L, 3500L, 4500L), kept)
        assertTrue(kept.all { it >= 4500L - 3000L })
        snapshot.forEach { it.bitmap.recycle() }
        buffer.clear()
    }

    @Test
    fun snapshotIsAscendingAndIndependentOfLaterAdds() {
        val buffer = SwingVideoBuffer(bufferDurationMs = 3000L)
        buffer.addFrame(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), 1000L)
        buffer.addFrame(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), 2000L)
        val first = buffer.snapshot()
        buffer.addFrame(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), 5000L)
        assertEquals(listOf(1000L, 2000L), first.map { it.timestampMs })
        assertEquals(listOf(2000L, 5000L), buffer.snapshot().map { it.timestampMs })
        first.forEach { it.bitmap.recycle() }
        buffer.clear()
    }

    @Test
    fun clearEmptiesSnapshot() {
        val buffer = SwingVideoBuffer()
        buffer.addFrame(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), 1L)
        buffer.clear()
        assertTrue(buffer.snapshot().isEmpty())
    }
}
