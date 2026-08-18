package io.github.loje0611.tennisdoc.feature.lab.pipeline

import android.graphics.Bitmap

data class SwingVideoFrame(
    val timestampMs: Long,
    val bitmap: Bitmap
)
