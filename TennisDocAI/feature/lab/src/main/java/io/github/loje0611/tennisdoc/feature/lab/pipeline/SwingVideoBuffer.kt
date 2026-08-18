package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentLinkedDeque

class SwingVideoBuffer(
    private val bufferDurationMs: Long = 3000L
) {
    private val frames = ConcurrentLinkedDeque<SwingVideoFrame>()

    fun addFrame(bitmap: Bitmap, timestampMs: Long) {
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        if (copy != null) {
            frames.addLast(SwingVideoFrame(timestampMs, copy))
        }

        val cutoff = timestampMs - bufferDurationMs
        while (frames.isNotEmpty() && frames.first().timestampMs < cutoff) {
            val oldFrame = frames.removeFirst()
            oldFrame.bitmap.recycle()
        }
    }

    fun snapshot(): List<SwingVideoFrame> {
        val snapshotFrames = mutableListOf<SwingVideoFrame>()
        val it = frames.iterator()
        while (it.hasNext()) {
            val f = it.next()
            if (!f.bitmap.isRecycled) {
                val copy = f.bitmap.copy(f.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                if (copy != null) {
                    snapshotFrames.add(SwingVideoFrame(f.timestampMs, copy))
                }
            }
        }
        return snapshotFrames
    }

    fun clear() {
        while (frames.isNotEmpty()) {
            val oldFrame = frames.removeFirst()
            oldFrame.bitmap.recycle()
        }
    }
}
