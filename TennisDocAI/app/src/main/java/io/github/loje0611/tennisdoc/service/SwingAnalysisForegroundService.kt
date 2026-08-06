package io.github.loje0611.tennisdoc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.loje0611.tennisdoc.BleConnectionState
import io.github.loje0611.tennisdoc.MainActivity
import io.github.loje0611.tennisdoc.R
import io.github.loje0611.tennisdoc.analysis.EdgeImpulseInputSpec
import io.github.loje0611.tennisdoc.analysis.KinematicAnalyzer
import io.github.loje0611.tennisdoc.analysis.RawSwingTelemetry
import io.github.loje0611.tennisdoc.analysis.SwingClassificationKeys
import io.github.loje0611.tennisdoc.analysis.SwingInferenceBuffer
import io.github.loje0611.tennisdoc.analysis.SwingKinematicsBuffer
import io.github.loje0611.tennisdoc.analysis.VolleyDetector
import kotlin.math.sqrt
import io.github.loje0611.tennisdoc.sensor.ImuPayloadParser
import io.github.loje0611.tennisdoc.sensor.MockBleDataSource
import io.github.loje0611.tennisdoc.sensor.MockSwingDataGenerator
import io.github.loje0611.tennisdoc.sensor.RealBleDataSource
import io.github.loje0611.tennisdoc.sensor.SensorDataSource
import io.github.loje0611.tennisdoc.session.SwingAnalysisSessionState
import io.github.loje0611.tennisdoc.ui.SwingLabelFormatter
import io.github.loje0611.tennisdoc.data.db.entity.SwingEventEntity
import io.github.loje0611.tennisdoc.data.db.entity.SwingSessionEntity
import io.github.loje0611.tennisdoc.data.repository.CalibrationStore
import io.github.loje0611.tennisdoc.data.repository.SwingHistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 화면이 꺼져도 BLE 수신 → 버퍼 → JNI 추론 → TTS까지 유지하는 포그라운드 서비스.
 * Mock 모드에서는 실제 BLE 없이 가상 데이터로 동일한 파이프라인을 구동한다.
 */
@AndroidEntryPoint
class SwingAnalysisForegroundService : Service() {

    @Inject lateinit var historyRepository: SwingHistoryRepository
    @Inject lateinit var calibrationStore: CalibrationStore

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    private val mainHandler = Handler(Looper.getMainLooper())

    private var dataSource: SensorDataSource? = null
    private var swingInferenceBuffer: SwingInferenceBuffer? = null
    private var kinematicsBuffer: SwingKinematicsBuffer? = null
    private var volleyDetector: VolleyDetector? = null
    private var kinematicAnalyzer: KinematicAnalyzer? = null
    private val currentSessionId = AtomicReference<String?>(null)
    private val sensorChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)

    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)

    private var durationJob: Job? = null
    private var pipelineStarted = false
    private var isMockMode = false
    private val sensorReadyFired = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_MOCK_STOP -> {
                stopAnalysisPipeline()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DEBUG_SIMULATE -> {
                val swingType = intent.getStringExtra(EXTRA_DEBUG_SWING_TYPE) ?: return START_NOT_STICKY
                val buffer = swingInferenceBuffer
                serviceScope.launch(Dispatchers.Default) {
                    buffer?.applyDebugSimulatedCooldown()
                    SwingAnalysisSessionState.updateSwingLabel(swingType)
                    if (!shouldSuppressTtsForIdle(swingType)) {
                        SwingAnalysisSessionState.incrementSwingCount(swingType)
                    }
                    if (buffer != null) {
                        speakSwingClassification(swingType)
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_MOCK_SWING -> {
                if (isMockMode && pipelineStarted) {
                    val swingType = intent.getStringExtra(EXTRA_DEBUG_SWING_TYPE)
                    if (swingType != null) {
                        handleMockSwing(swingType)
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_SEND_BLE_COMMAND -> {
                val cmd = intent.getStringExtra(EXTRA_BLE_COMMAND)
                if (cmd != null) {
                    dataSource?.sendCommand(cmd)
                }
                return START_NOT_STICKY
            }
            ACTION_MOCK_START -> {
                val notification = buildNotification(mock = true)
                startForegroundCompat(notification)
                if (!pipelineStarted) {
                    pipelineStarted = true
                    isMockMode = true
                    SwingAnalysisSessionState.setMockMode(true)
                    startAnalysisPipeline()
                }
            }
            ACTION_START, null -> {
                val notification = buildNotification(mock = false)
                startForegroundCompat(notification)
                if (!pipelineStarted) {
                    pipelineStarted = true
                    isMockMode = false
                    startAnalysisPipeline()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAnalysisPipeline()
        serviceJob.cancel()
        super.onDestroy()
    }

    // ── Pipeline Lifecycle ────────────────────────────────────────────────

    private fun startAnalysisPipeline() {
        val vd = VolleyDetector()
        val ka = KinematicAnalyzer()
        volleyDetector = vd
        kinematicAnalyzer = ka
        swingInferenceBuffer = SwingInferenceBuffer(vd)
        kinematicsBuffer = SwingKinematicsBuffer()
        currentSessionId.set(null)
        sensorReadyFired.set(false)
        SwingAnalysisSessionState.clearSensorReady()
        SwingAnalysisSessionState.setPipelineRunning(true)

        tts = TextToSpeech(applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                ttsReady.set(false)
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            engine.setSpeechRate(TTS_SPEECH_RATE)
            val langResult = engine.setLanguage(Locale.US)
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                ttsReady.set(false)
            } else {
                ttsReady.set(true)
            }
        }

        val buffer = checkNotNull(swingInferenceBuffer) { "SwingInferenceBuffer not initialized" }
        val kBuffer = checkNotNull(kinematicsBuffer) { "SwingKinematicsBuffer not initialized" }

        serviceScope.launch(Dispatchers.Default) {
            calibrationStore.configFlow.collect { cfg ->
                vd.accelThresholdSq = cfg.volleyAccelThresholdSq
                vd.maxVolleyDurationMs = cfg.volleyMaxDurationMs.toLong()
                vd.gyroFollowThroughThresholdSq = cfg.gyroFollowThroughThresholdSq
                ka.powerMax = cfg.powerMaxNormalization
                ka.spinMax = cfg.spinMaxNormalization
                ka.smoothnessWorstVariance = cfg.smoothnessWorstVariance
                Log.d(TAG, "Calibration updated: $cfg")
            }
        }

        if (!isMockMode) {
            serviceScope.launch(Dispatchers.Default) {
                for (sample in sensorChannel) {
                    kBuffer.addSample(sample)

                    val label = buffer.onSample(sample) ?: continue
                    SwingAnalysisSessionState.updateSwingLabel(label)

                    val isIdle = shouldSuppressTtsForIdle(label)
                    if (!isIdle) {
                        SwingAnalysisSessionState.incrementSwingCount(label)
                    }
                    speakSwingClassification(label)

                    if (!isIdle) {
                        processSwingEvent(label, kBuffer, vd, ka)
                    }
                }
            }
        }

        val onConnectionState: (BleConnectionState) -> Unit = { state ->
            SwingAnalysisSessionState.updateConnection(state)
            if (state is BleConnectionState.Connected) {
                val connectTime = System.currentTimeMillis()
                SwingAnalysisSessionState.updateSessionStartTime(connectTime)
                val sid = UUID.randomUUID().toString()
                currentSessionId.set(sid)
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val provisional = SwingSessionEntity(
                            sessionId = sid,
                            sessionName = SwingSessionEntity.formatSessionName(connectTime),
                            startTime = connectTime,
                        )
                        historyRepository.insertProvisionalSession(provisional)
                    } catch (e: Exception) {
                        Log.w(TAG, "Provisional session insert failed", e)
                    }
                }
                durationJob?.cancel()
                durationJob = serviceScope.launch(Dispatchers.Default) {
                    var seconds = 0L
                    while (SwingAnalysisSessionState.connectionState.value.isConnected) {
                        SwingAnalysisSessionState.updateSessionDuration(seconds)
                        kotlinx.coroutines.delay(1000L)
                        seconds++
                    }
                }
            }
            if (state.isDisconnectedOrError) {
                serviceScope.launch(Dispatchers.Default) {
                    buffer.reset()
                    kBuffer.reset()
                }
                SwingAnalysisSessionState.updateSwingLabel("")
            }
        }

        if (isMockMode) {
            dataSource = MockBleDataSource(onConnectionState)
        } else {
            dataSource = RealBleDataSource(
                applicationContext,
                onConnectionState,
                onSensorPayload = { text ->
                    if (text.contains("DONE", ignoreCase = true)) {
                        SwingAnalysisSessionState.triggerCalibrationDone()
                        return@RealBleDataSource
                    }
                    val sample = ImuPayloadParser.parseLine(text) ?: return@RealBleDataSource
                    if (sensorReadyFired.compareAndSet(false, true)) {
                        SwingAnalysisSessionState.triggerSensorReady()
                    }
                    if (sensorChannel.trySend(sample).isFailure) {
                        Log.w(TAG, "Sensor channel buffer full — sample dropped")
                    }
                },
            )
        }

        dataSource?.connect()
    }

    private fun stopAnalysisPipeline() {
        pipelineStarted = false
        SwingAnalysisSessionState.setPipelineRunning(false)
        durationJob?.cancel()
        durationJob = null
        sensorChannel.close()
        dataSource?.release()
        dataSource = null
        swingInferenceBuffer = null
        kinematicsBuffer = null

        val engine = tts
        tts = null
        ttsReady.set(false)
        engine?.stop()
        engine?.shutdown()

        val totalSwings = SwingAnalysisSessionState.swingCount.value
        val durationSecs = SwingAnalysisSessionState.sessionDurationSeconds.value
        val startTime = SwingAnalysisSessionState.sessionStartTimeMillis
        val breakdownMap = SwingAnalysisSessionState.swingBreakdown.value

        val savedSessionId = currentSessionId.getAndSet(null)
        val savedVolleyDetector = volleyDetector
        val wasMock = isMockMode
        isMockMode = false
        volleyDetector = null
        kinematicAnalyzer = null

        if (totalSwings > 0 && durationSecs > 0 && startTime > 0 && savedSessionId != null) {
            val endTime = System.currentTimeMillis()

            val breakdownNormalized = breakdownMap.mapKeys { SwingClassificationKeys.normalize(it.key) }
            val fhVolley = breakdownNormalized["forehand volley"] ?: 0
            val bhVolley = breakdownNormalized["backhand volley"] ?: 0

            serviceScope.launch(Dispatchers.IO) {
                withContext(NonCancellable) {
                    try {
                        historyRepository.finalizeSession(
                            sessionId = savedSessionId,
                            endTime = endTime,
                            totalSwingCount = totalSwings,
                            durationMillis = durationSecs * 1000L,
                            fhVolley = fhVolley,
                            bhVolley = bhVolley,
                            breakdownNormalized = breakdownNormalized,
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Session finalize failed", e)
                    }
                    SwingAnalysisSessionState.resetSessionUiState()
                }
            }
        } else {
            if (savedSessionId != null) {
                serviceScope.launch(Dispatchers.IO) {
                    withContext(NonCancellable) {
                        try {
                            historyRepository.deleteSession(savedSessionId)
                        } catch (e: Exception) {
                            Log.w(TAG, "Session delete failed", e)
                        }
                        SwingAnalysisSessionState.resetSessionUiState()
                    }
                }
            } else {
                SwingAnalysisSessionState.resetSessionUiState()
            }
        }
    }

    // ── Real swing event processing (shared between real & mock paths) ───

    private fun processSwingEvent(
        label: String,
        kBuffer: SwingKinematicsBuffer,
        vd: VolleyDetector,
        ka: KinematicAnalyzer,
    ) {
        serviceScope.launch(Dispatchers.Default) {
            val snapshot = kBuffer.snapshot()
            val metrics = if (snapshot.isNotEmpty()) ka.analyze(snapshot) else null

            val debugText = buildString {
                appendLine("── Swing #${SwingAnalysisSessionState.swingCount.value} ──")
                appendLine("Label: $label")
                appendLine(vd.lastDebugInfo)
                if (metrics != null) {
                    appendLine(ka.lastDebugInfo)
                    appendLine("Smoothness: ${metrics.smoothness}  Var(j)=%.4f".format(ka.lastJerkVariance))
                }
            }.trimEnd()
            SwingAnalysisSessionState.updateLastRawSwingData(debugText)
            Log.d(TAG, debugText)

            val detectorRaw = RawSwingTelemetry(
                maxAccelG = sqrt(vd.lastPeakAccelSq.coerceAtLeast(0f)) / 9.81f,
                durationMs = vd.lastDurationMs
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt(),
                gyroFollowDps = sqrt(vd.lastAvgGyroSq.coerceAtLeast(0f)),
            )
            val computedRaw = RawSwingTelemetry.fromSnapshot(
                snapshot,
                accelThresholdSq = vd.accelThresholdSq,
            )

            val sid = currentSessionId.get()
            if (sid != null && metrics != null) {
                val normalizedKey = SwingClassificationKeys.normalize(label)
                val rawForEvent = if (SwingClassificationKeys.isVolleyCategory(normalizedKey)) {
                    detectorRaw.withFallback(computedRaw)
                } else {
                    computedRaw.withFallback(detectorRaw)
                }
                insertSwingEvent(sid, normalizedKey, metrics, rawForEvent)
            }
        }
    }

    // ── Mock Swing (True E2E — VolleyDetector + AI classifier pipeline) ─

    private fun handleMockSwing(requestedType: String) {
        val buffer = swingInferenceBuffer ?: return
        val kBuffer = kinematicsBuffer ?: return
        val vd = volleyDetector ?: return
        val ka = kinematicAnalyzer ?: return

        serviceScope.launch(Dispatchers.Default) {
            buffer.reset()

            val samples = MockSwingDataGenerator.generate(requestedType)
            var pipelineLabel: String? = null

            for ((idx, s) in samples.withIndex()) {
                kBuffer.addSample(s)
                if (pipelineLabel == null && idx < EdgeImpulseInputSpec.WINDOW_SAMPLES) {
                    pipelineLabel = buffer.onSample(s)
                }
            }

            val normalizedRequest = SwingClassificationKeys.normalize(requestedType)
            val label: String = pipelineLabel
                ?: if (!SwingClassificationKeys.isVolleyCategory(normalizedRequest)) {
                    Log.d(TAG, "Mock E2E: JNI unavailable, fallback to requested stroke type: $normalizedRequest")
                    normalizedRequest
                } else {
                    Log.d(TAG, "Mock E2E: volley rejected by VolleyDetector gate — $requestedType")
                    return@launch
                }

            if (SwingClassificationKeys.isIdle(label)) return@launch

            SwingAnalysisSessionState.updateSwingLabel(label)
            SwingAnalysisSessionState.incrementSwingCount(label)
            speakSwingClassification(label)
            processSwingEvent(label, kBuffer, vd, ka)
        }
    }

    private fun insertSwingEvent(
        sessionId: String,
        normalizedKey: String,
        metrics: io.github.loje0611.tennisdoc.analysis.SwingMetrics,
        raw: RawSwingTelemetry,
    ) {
        val event = SwingEventEntity(
            sessionId = sessionId,
            categoryKey = normalizedKey,
            timestampMillis = System.currentTimeMillis(),
            power = metrics.power,
            spin = metrics.spin,
            timing = metrics.timing,
            fluidity = metrics.smoothness,
            stability = metrics.stability,
            consistency = metrics.consistency,
            rawMaxAccel = raw.maxAccelG,
            rawDurationMs = raw.durationMs,
            rawGyroFollow = raw.gyroFollowDps,
        )
        serviceScope.launch(Dispatchers.IO) {
            try {
                historyRepository.insertSwingEvent(event)
            } catch (e: Exception) {
                Log.w(TAG, "SwingEvent DB insert failed", e)
            }
        }
    }

    // ── TTS ───────────────────────────────────────────────────────────────

    private fun speakSwingClassification(rawLabel: String) {
        if (shouldSuppressTtsForIdle(rawLabel)) return

        val phrase = SwingLabelFormatter.phraseForTtsOnly(rawLabel)
        if (phrase.isBlank()) return

        mainHandler.post {
            val engine = tts
            if (engine == null || !ttsReady.get()) return@post
            if (shouldSuppressTtsForIdle(phrase)) return@post

            engine.stop()
            val utteranceId = "swing_${System.nanoTime()}"
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null)
            }
            if (result == TextToSpeech.ERROR) { /* 무시 */ }
        }
    }

    private fun shouldSuppressTtsForIdle(text: String): Boolean {
        return SwingClassificationKeys.isIdle(text)
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(mock: Boolean = false): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or pendingImmutableFlag(),
        )

        val launcherIconBitmap = decodeNotificationLargeLauncherBitmap()
        val title = if (mock) "Mock Sensor Active" else getString(R.string.notification_title)
        val text = if (mock) "Simulated swing data pipeline running" else getString(R.string.notification_text)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .apply {
                if (launcherIconBitmap != null) {
                    setSmallIcon(IconCompat.createWithBitmap(launcherIconBitmap))
                    setLargeIcon(launcherIconBitmap)
                } else {
                    setSmallIcon(R.drawable.ic_notification)
                }
            }
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun decodeNotificationLargeLauncherBitmap(): Bitmap? {
        BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_round)?.let { return it }
        BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)?.let { return it }
        return bitmapFromLauncherDrawable(R.mipmap.ic_launcher_round)
            ?: bitmapFromLauncherDrawable(R.mipmap.ic_launcher)
    }

    private fun bitmapFromLauncherDrawable(resId: Int): Bitmap? {
        val drawable = ResourcesCompat.getDrawable(resources, resId, theme) ?: return null
        val w = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        val h = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    private fun pendingImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_START = "io.github.loje0611.tennisdoc.action.START_ANALYSIS"
        const val ACTION_STOP = "io.github.loje0611.tennisdoc.action.STOP_ANALYSIS"
        const val ACTION_DEBUG_SIMULATE = "io.github.loje0611.tennisdoc.action.DEBUG_SIMULATE_SWING"
        const val ACTION_SEND_BLE_COMMAND = "io.github.loje0611.tennisdoc.action.SEND_BLE_COMMAND"
        const val ACTION_MOCK_START = "io.github.loje0611.tennisdoc.action.MOCK_START"
        const val ACTION_MOCK_STOP = "io.github.loje0611.tennisdoc.action.MOCK_STOP"
        const val ACTION_MOCK_SWING = "io.github.loje0611.tennisdoc.action.MOCK_SWING"
        const val EXTRA_DEBUG_SWING_TYPE = "extra_debug_swing_type"
        const val EXTRA_BLE_COMMAND = "extra_ble_command"

        private const val CHANNEL_ID = "swing_analysis_fg"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "SwingFgService"
        private const val TTS_SPEECH_RATE = 1.0f

        fun start(context: Context) {
            val intent = Intent(context, SwingAnalysisForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestStop(context: Context) {
            val intent = Intent(context, SwingAnalysisForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun startMock(context: Context) {
            val intent = Intent(context, SwingAnalysisForegroundService::class.java).apply {
                action = ACTION_MOCK_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopMock(context: Context) {
            val intent = Intent(context, SwingAnalysisForegroundService::class.java).apply {
                action = ACTION_MOCK_STOP
            }
            context.startService(intent)
        }

        fun triggerMockSwing(context: Context, swingType: String) {
            val intent = Intent(context, SwingAnalysisForegroundService::class.java).apply {
                action = ACTION_MOCK_SWING
                putExtra(EXTRA_DEBUG_SWING_TYPE, swingType)
            }
            context.startService(intent)
        }

        fun requestDebugSimulation(context: Context, swingType: String) {
            val intent = Intent(context, SwingAnalysisForegroundService::class.java).apply {
                action = ACTION_DEBUG_SIMULATE
                putExtra(EXTRA_DEBUG_SWING_TYPE, swingType)
            }
            context.startService(intent)
        }

        fun requestSendBleCommand(context: Context, cmd: String) {
            val intent = Intent(context, SwingAnalysisForegroundService::class.java).apply {
                action = ACTION_SEND_BLE_COMMAND
                putExtra(EXTRA_BLE_COMMAND, cmd)
            }
            context.startService(intent)
        }
    }
}
