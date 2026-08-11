package com.spike.mediapipe.benchmark

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BenchmarkActivity : AppCompatActivity() {
    private val TAG = "SPIKE01"
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var tvStats: TextView
    private lateinit var btnAuto: Button
    private lateinit var cameraExecutor: ExecutorService

    private var poseLandmarker: PoseLandmarker? = null
    private var currentResolution = Size(640, 480)
    private var resName = "480p"

    private var currentFps = 0f
    private val fpsWindow = mutableListOf<Long>()
    private var lastUiUpdateMs = 0L

    private var isAutoBenchmarking = false
    private var autoBenchmarkJob: Job? = null

    // Per-resolution stats for summary
    private val latencyList = mutableListOf<Long>()
    private val confidenceList = mutableListOf<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)

        previewView = findViewById(R.id.view_finder)
        overlayView = findViewById(R.id.overlay)
        tvStats = findViewById(R.id.tv_stats)
        btnAuto = findViewById(R.id.btn_auto_benchmark)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.btn_1080p).setOnClickListener { changeResolution(Size(1920, 1080), "1080p") }
        findViewById<Button>(R.id.btn_720p).setOnClickListener { changeResolution(Size(1280, 720), "720p") }
        findViewById<Button>(R.id.btn_480p).setOnClickListener { changeResolution(Size(640, 480), "480p") }
        btnAuto.setOnClickListener { startAutoBenchmark() }

        if (allPermissionsGranted()) {
            initAndStart()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initAndStart()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun initAndStart() {
        setupPoseLandmarker()
        startCamera()
    }

    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, input -> returnLivestreamResult(result, input) }
                .setErrorListener { error -> Log.e(TAG, "MediaPipe Error: ${error.message}") }
                .build()
            poseLandmarker = PoseLandmarker.createFromOptions(this, options)
            Log.i(TAG, "PoseLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PoseLandmarker: ${e.message}", e)
            runOnUiThread {
                tvStats.text = "ERROR: ${e.message}"
                Toast.makeText(this, "PoseLandmarker init failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTimeMs = finishTimeMs - result.timestampMs()

        // Calculate FPS from rolling window
        fpsWindow.add(finishTimeMs)
        if (fpsWindow.size > 30) fpsWindow.removeAt(0)
        if (fpsWindow.size > 1) {
            currentFps = 1000f * (fpsWindow.size - 1) / (fpsWindow.last() - fpsWindow.first())
        }

        val numPoses = result.landmarks().size
        var avgConf = 0f
        if (numPoses > 0) {
            avgConf = result.landmarks()[0].map { it.presence().orElse(0f) }.average().toFloat()
        }

        // Collect for summary
        latencyList.add(inferenceTimeMs)
        confidenceList.add(avgConf)

        val fpsStr = String.format(java.util.Locale.US, "%.1f", currentFps)
        val confStr = String.format(java.util.Locale.US, "%.3f", avgConf)
        Log.i(TAG, "FRAME|$resName|${inferenceTimeMs}ms|${fpsStr}fps|poses=$numPoses|conf=$confStr")

        runOnUiThread {
            if (finishTimeMs - lastUiUpdateMs > 400) {
                tvStats.text = "Resolution: $resName\nFPS: $fpsStr\nLatency: ${inferenceTimeMs}ms\nPoses: $numPoses\nConfidence: $confStr"
                lastUiUpdateMs = finishTimeMs
            }
            overlayView.setResults(result, input.height, input.width)
        }
    }

    private fun changeResolution(size: Size, name: String) {
        if (isAutoBenchmarking) return
        currentResolution = size
        resName = name
        fpsWindow.clear()
        startCamera()
    }

    @Suppress("DEPRECATION")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(currentResolution)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(currentResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        analyzeImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                Log.i(TAG, "Camera bound with resolution: $resName ($currentResolution)")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        if (poseLandmarker == null) {
            imageProxy.close()
            return
        }
        try {
            val bitmap = Bitmap.createBitmap(
                imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
            )
            val buffer = imageProxy.planes[0].buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            imageProxy.close()

            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestamp = SystemClock.uptimeMillis()
            poseLandmarker?.detectAsync(mpImage, timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis error: ${e.message}")
            imageProxy.close()
        }
    }

    private fun startAutoBenchmark() {
        if (isAutoBenchmarking) return
        isAutoBenchmarking = true
        btnAuto.isEnabled = false
        btnAuto.text = "Benchmarking..."
        Toast.makeText(this, "Auto Benchmark started (3 x 30sec)", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "========== AUTO BENCHMARK STARTED ==========")

        autoBenchmarkJob = CoroutineScope(Dispatchers.Main).launch {
            val resolutions = listOf(
                Size(640, 480) to "480p",
                Size(1280, 720) to "720p",
                Size(1920, 1080) to "1080p"
            )

            for ((size, name) in resolutions) {
                currentResolution = size
                resName = name
                fpsWindow.clear()
                latencyList.clear()
                confidenceList.clear()

                Log.i(TAG, ">>> START_BENCHMARK|$resName")
                startCamera()

                // Wait 2 seconds for camera to stabilize
                delay(2000)
                latencyList.clear()
                confidenceList.clear()

                // Benchmark for 30 seconds
                delay(30000)

                // Print summary
                val avgLatency = if (latencyList.isNotEmpty()) latencyList.average() else 0.0
                val minLatency = latencyList.minOrNull() ?: 0
                val maxLatency = latencyList.maxOrNull() ?: 0
                val p50Latency = percentile(latencyList, 50)
                val p95Latency = percentile(latencyList, 95)
                val avgConfidence = if (confidenceList.isNotEmpty()) confidenceList.average() else 0.0
                val totalFrames = latencyList.size
                val measuredFps = if (totalFrames > 1) totalFrames / 30.0 else 0.0

                Log.i(TAG, "=== SUMMARY|$resName|frames=$totalFrames|avg_fps=${String.format("%.1f", measuredFps)}|avg_latency=${String.format("%.1f", avgLatency)}ms|min=${minLatency}ms|max=${maxLatency}ms|p50=${p50Latency}ms|p95=${p95Latency}ms|avg_conf=${String.format("%.3f", avgConfidence)} ===")
                Log.i(TAG, ">>> END_BENCHMARK|$resName")
            }

            isAutoBenchmarking = false
            btnAuto.isEnabled = true
            btnAuto.text = "Start Auto Benchmark"
            Log.i(TAG, "========== AUTO BENCHMARK COMPLETE ==========")
            Toast.makeText(this@BenchmarkActivity, "Benchmark Complete! Check logcat.", Toast.LENGTH_LONG).show()
        }
    }

    private fun percentile(list: List<Long>, p: Int): Long {
        if (list.isEmpty()) return 0
        val sorted = list.sorted()
        val index = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    override fun onDestroy() {
        super.onDestroy()
        autoBenchmarkJob?.cancel()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }
}
