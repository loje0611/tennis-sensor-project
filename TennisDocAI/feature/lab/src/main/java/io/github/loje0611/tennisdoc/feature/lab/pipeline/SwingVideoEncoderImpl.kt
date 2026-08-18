package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SwingVideoEncoderImpl : SwingVideoEncoder {
    override suspend fun encodeToMp4(
        frames: List<SwingVideoFrame>,
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext false

        var muxer: MediaMuxer? = null
        var codec: MediaCodec? = null
        var surface: Surface? = null
        var success = false

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps
            var pts = 0L

            for (frame in frames) {
                if (frame.bitmap.isRecycled) continue

                val canvas = surface.lockCanvas(null)
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    val srcRect = Rect(0, 0, frame.bitmap.width, frame.bitmap.height)
                    
                    // Maintain aspect ratio or just fill center? Spec says 480x640.
                    // If camera preview is 640x480 but device is rotated, we assume bitmap is processed.
                    // We'll fill exactly since we want 480x640 MP4.
                    val dstRect = Rect(0, 0, width, height)
                    canvas.drawBitmap(frame.bitmap, srcRect, dstRect, null)
                    surface.unlockCanvasAndPost(canvas)
                }

                var encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (encoderStatus >= 0 || encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw IllegalStateException("format changed twice")
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (encoderStatus >= 0) {
                        val encodedData = codec.getOutputBuffer(encoderStatus)
                        if (encodedData != null) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0) {
                                if (!muxerStarted) throw IllegalStateException("muxer hasn't started")
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                bufferInfo.presentationTimeUs = pts
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                                pts += frameDurationUs
                            }
                            codec.releaseOutputBuffer(encoderStatus, false)
                        }
                    }
                    encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            codec.signalEndOfInputStream()
            var encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 10000)
            while (encoderStatus >= 0) {
                val encodedData = codec.getOutputBuffer(encoderStatus)
                if (encodedData != null) {
                    if (bufferInfo.size != 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        bufferInfo.presentationTimeUs = pts
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(encoderStatus, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
                encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 10000)
            }

            success = true
        } catch (e: Exception) {
            e.printStackTrace()
            success = false
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {}
            try {
                if (muxer != null && success) {
                    muxer.stop()
                    muxer.release()
                } else if (muxer != null) {
                    muxer.release()
                }
            } catch (e: Exception) {}
            surface?.release()

            if (!success && outputFile.exists()) {
                outputFile.delete()
            }
        }
        success
    }
}
