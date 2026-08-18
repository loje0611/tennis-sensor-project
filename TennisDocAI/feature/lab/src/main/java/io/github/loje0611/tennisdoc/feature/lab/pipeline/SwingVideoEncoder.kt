package io.github.loje0611.tennisdoc.feature.lab.pipeline

import java.io.File

interface SwingVideoEncoder {
    suspend fun encodeToMp4(
        frames: List<SwingVideoFrame>,
        outputFile: File,
        width: Int = 480,
        height: Int = 640,
        fps: Int = 30,
        bitrate: Int = 1_500_000
    ): Boolean
}
