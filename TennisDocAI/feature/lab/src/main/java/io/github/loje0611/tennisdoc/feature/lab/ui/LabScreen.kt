package io.github.loje0611.tennisdoc.feature.lab.ui

import android.Manifest
import android.os.Build
import android.util.Size
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import io.github.loje0611.tennisdoc.core.vision.model.PoseFrame
import io.github.loje0611.tennisdoc.feature.lab.landmarker.MediaPipePoseLandmarkerWrapper
import io.github.loje0611.tennisdoc.feature.lab.pipeline.PoseAnalysisAnalyzer
import java.util.concurrent.Executors

private fun labBleRuntimePermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.BLUETOOTH)
        add(Manifest.permission.BLUETOOTH_ADMIN)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LabScreen(
    viewModel: LabViewModel? = null,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val blePermissionsState = rememberMultiplePermissionsState(labBleRuntimePermissions())

    LaunchedEffect(cameraPermissionState.status.isGranted, blePermissionsState.allPermissionsGranted) {
        when {
            !cameraPermissionState.status.isGranted -> {
                cameraPermissionState.launchPermissionRequest()
            }
            !blePermissionsState.allPermissionsGranted -> {
                blePermissionsState.launchMultiplePermissionRequest()
            }
        }
    }
    
    if (cameraPermissionState.status.isGranted) {
        CameraPreviewWithOverlay(
            viewModel = viewModel,
            blePermissionsGranted = blePermissionsState.allPermissionsGranted,
            onRequestBlePermissions = { blePermissionsState.launchMultiplePermissionRequest() },
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "카메라 권한이 필요합니다.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("권한 허용")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    viewModel: LabViewModel?,
    blePermissionsGranted: Boolean,
    onRequestBlePermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val uiState by viewModel?.uiState?.collectAsStateWithLifecycle() 
        ?: remember { mutableStateOf(LabUiState()) }

    var currentPoseFrame by remember { mutableStateOf<PoseFrame?>(null) }
    var fpsText by remember { mutableStateOf("0.0 FPS | 0ms") }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val landmarkerWrapper = remember {
        MediaPipePoseLandmarkerWrapper(context)
    }

    // Auto-connect sensor when BLE permission is granted and sensor is not connected
    LaunchedEffect(blePermissionsGranted, uiState.isSensorConnected, uiState.isSensorScanning) {
        if (blePermissionsGranted && !uiState.isSensorConnected && !uiState.isSensorScanning) {
            viewModel?.connectSensor()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP || 
                event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }, ContextCompat.getMainExecutor(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraExecutor.shutdown()
            landmarkerWrapper.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder()
                        .setTargetResolution(Size(640, 480))
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        
                    var lastTime = System.currentTimeMillis()
                    var frameCount = 0
                    
                    val analyzer = PoseAnalysisAnalyzer(
                        landmarkerWrapper = landmarkerWrapper,
                        onPoseExtracted = { poseFrame ->
                            currentPoseFrame = poseFrame
                            if (poseFrame != null) {
                                viewModel?.onPoseDetected(poseFrame)
                            }
                            
                            frameCount++
                            val currentTime = System.currentTimeMillis()
                            val diff = currentTime - lastTime
                            if (diff >= 1000) {
                                val fps = frameCount * 1000f / diff
                                val msPerFrame = diff / frameCount
                                fpsText = String.format("%.1f FPS | %dms", fps, msPerFrame)
                                frameCount = 0
                                lastTime = currentTime
                            }
                        }
                    )
                    
                    imageAnalyzer.setAnalyzer(cameraExecutor, analyzer)
                    
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        PoseOverlayCanvas(
            poseFrame = currentPoseFrame,
            modifier = Modifier.fillMaxSize()
        )
        
        // UI Controls & Feedback Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 이상치 / 피로도 경고 배너
            LabAnomalyAlertBanner(report = uiState.latestAnomalyReport)

            // 2. 세션 제어 헤더
            LabSessionControlHeader(
                selectedDrill = uiState.selectedDrill,
                isSessionActive = uiState.isSessionActive,
                sessionDurationSeconds = uiState.sessionDurationSeconds,
                swingCount = uiState.swingCount,
                isSensorConnected = uiState.isSensorConnected,
                isSensorScanning = uiState.isSensorScanning,
                onConnectSensor = {
                    if (blePermissionsGranted) {
                        viewModel?.connectSensor()
                    } else {
                        onRequestBlePermissions()
                    }
                },
                onStartSession = {
                    val started = viewModel?.startSession() ?: false
                    if (!started) {
                        Toast.makeText(
                            context,
                            "센서를 먼저 연결해 주세요",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onFinishSession = { viewModel?.finishSession() }
            )

            // 3. 드릴 선택 바
            DrillSelectorBar(
                selectedDrill = uiState.selectedDrill,
                isSessionActive = uiState.isSessionActive,
                onSelectDrill = { viewModel?.selectDrill(it) }
            )

            // FPS Overlay (개발자 모드 활성화 시에만 노출)
            if (uiState.isDebugModeEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = fpsText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

            // 4. 실시간 융합 피드백 카드 (하단)
            LabRealtimeFeedbackCard(
                fusedSwing = uiState.latestFusedSwing,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
