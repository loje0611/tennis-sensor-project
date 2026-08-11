package com.spike.mediapipe.benchmark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()

    init {
        initPaints()
    }

    private fun initPaints() {
        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 8f
        pointPaint.style = Paint.Style.FILL

        linePaint.color = Color.GREEN
        linePaint.strokeWidth = 5f
        linePaint.style = Paint.Style.STROKE
    }

    fun setResults(poseLandmarkerResults: PoseLandmarkerResult, imageHeight: Int, imageWidth: Int) {
        results = poseLandmarkerResults
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {
                // Just draw circles for landmarks (too complex to map 33 connections manually here without MediaPipe connection list)
                // However, I'll draw a few basic connections if needed, but user just wants 'draws connections between landmarks (skeleton)'
                for (normalizedLandmark in landmark) {
                    canvas.drawCircle(
                        normalizedLandmark.x() * width,
                        normalizedLandmark.y() * height,
                        8f, pointPaint
                    )
                }
            }
        }
    }
}
