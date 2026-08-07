package io.github.loje0611.tennisdoc.ui.practice

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.loje0611.tennisdoc.core.sensor.BleConnectionState
import io.github.loje0611.tennisdoc.MainViewModel
import io.github.loje0611.tennisdoc.core.model.SwingClassificationKeys
import io.github.loje0611.tennisdoc.core.ui.SwingLabelFormatter
import io.github.loje0611.tennisdoc.core.ui.theme.MichromaFont
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

private val CapsuleShape = RoundedCornerShape(percent = 50)
private val PillHeight = 56.dp


private const val DebugSwingForehandTopspin = "forehand topspin"
private const val DebugSwingForehandSlice = "forehand slice"
private const val DebugSwingForehandVolley = "forehand volley"
private const val DebugSwingBackhandTopspin = "backhand topspin"
private const val DebugSwingBackhandSlice = "backhand slice"
private const val DebugSwingBackhandVolley = "backhand volley"

/**
 * 디버그 터치 영역을 3×3(9칸)으로 나눈 뒤 시계 위치에 대응하는 스윙 라벨을 반환한다.
 * 상단 행: 11시(좌)·12시(중)·1시(우), 중간 행: 9시(좌)·3시(우), 하단 중앙: 6시.
 * (가운데·좌하·우하는 요청에 없는 칸이라 null — 터치 무시)
 */
private fun debugSwingLabelForNinePatch(row: Int, col: Int): String? =
    when (row * 3 + col) {
        0 -> DebugSwingBackhandVolley // 11시 — 좌상
        1 -> DebugSwingBackhandTopspin // 12시 — 상중
        2 -> DebugSwingForehandVolley // 1시 — 우상
        3 -> DebugSwingForehandSlice // 9시 — 좌중
        5 -> DebugSwingForehandTopspin // 3시 — 우중
        7 -> DebugSwingBackhandSlice // 6시 — 하중
        else -> null
    }

private fun runtimePermissionList(): List<String> = buildList {
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

private fun isDashboardEmptyOrIdle(raw: String): Boolean {
    val t = raw.trim()
    if (t.isEmpty()) return true
    return SwingClassificationKeys.isIdle(t)
}

private fun centerDisplayKey(raw: String): String =
    if (isDashboardEmptyOrIdle(raw)) "" else raw

@Composable
@ReadOnlyComposable
private fun accentColorForSwingLabel(rawLabel: String): Color =
    io.github.loje0611.tennisdoc.core.ui.accentColorForCategory(rawLabel)

private fun neonTextStyle(
    base: TextStyle,
    color: Color,
): TextStyle {
    return base.copy(
        color = color,
        shadow = Shadow(
            color = color.copy(alpha = 0.85f),
            offset = Offset.Zero,
            blurRadius = 28f,
        ),
    )
}

private fun formatTimer(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
}

@Composable
fun CyberpunkBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val gradient = Brush.linearGradient(
            colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.2f), Color(0xFF0055FF).copy(alpha = 0.05f)),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        
        val path1 = Path().apply {
            moveTo(0f, h * 0.35f)
            cubicTo(w * 0.3f, h * 0.45f, w * 0.7f, h * 0.25f, w, h * 0.45f)
        }
        drawPath(path1, brush = gradient, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        
        val path2 = Path().apply {
            moveTo(0f, h * 0.65f)
            cubicTo(w * 0.4f, h * 0.75f, w * 0.8f, h * 0.55f, w, h * 0.85f)
        }
        drawPath(path2, brush = gradient, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w/2, h/2),
                radius = w * 0.7f
            ),
            center = Offset(w/2, h/2),
            radius = w * 0.7f
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PracticeScreen(
    viewModel: MainViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val permissionsState = rememberMultiplePermissionsState(runtimePermissionList())

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val swingLabel by viewModel.detectedSwingLabel.collectAsStateWithLifecycle()
    val swingCount by viewModel.swingCount.collectAsStateWithLifecycle()
    val sessionDuration by viewModel.sessionDurationSeconds.collectAsStateWithLifecycle()
    val debugEnabled by viewModel.isDebugModeEnabled.collectAsStateWithLifecycle()
    
    val centerKey = centerDisplayKey(swingLabel)

    var debugToastShown by remember { mutableStateOf(false) }
    LaunchedEffect(debugEnabled) {
        if (debugEnabled && !debugToastShown) {
            Toast.makeText(context, "Debugging Mode Active", Toast.LENGTH_SHORT).show()
            debugToastShown = true
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState is BleConnectionState.Error) {
            val msg = when ((connectionState as BleConnectionState.Error).reason) {
                BleConnectionState.ErrorReason.BluetoothOff -> "블루투스가 꺼져 있습니다. 설정에서 켜 주세요."
                BleConnectionState.ErrorReason.PermissionDenied -> "블루투스 권한이 필요합니다. 설정에서 허용해 주세요."
                BleConnectionState.ErrorReason.ScanFailed -> "센서 스캔에 실패했습니다. 다시 시도해 주세요."
                BleConnectionState.ErrorReason.ConnectionTimeout -> "센서 연결이 시간 초과되었습니다."
                BleConnectionState.ErrorReason.MaxReconnectReached -> "재연결에 실패했습니다. 센서를 확인해 주세요."
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val (showCancelScanDialog, setShowCancelScanDialog) = remember { mutableStateOf(false) }

    if (showCancelScanDialog) {
        AlertDialog(
            onDismissRequest = { setShowCancelScanDialog(false) },
            containerColor = SwingTheme.colors.cardSurface,
            title = {
                Text(
                    text = "스캔 취소",
                    color = SwingTheme.colors.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "센서 검색을 취소하시겠습니까?",
                    color = SwingTheme.colors.onBackground.copy(alpha = 0.85f),
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        setShowCancelScanDialog(false)
                    }
                ) {
                    Text("예", color = SwingTheme.colors.connectBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { setShowCancelScanDialog(false) }
                ) {
                    Text("아니오", color = SwingTheme.colors.subGray)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwingTheme.colors.background)
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        CyberpunkBackground()
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            PracticeAppHeader(
                connectionState = connectionState,
                showDebugBadge = debugEnabled,
                onTitleClick = { viewModel.onDebugActivationAreaTap() },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        targetState = centerKey,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(360)) togetherWith
                                fadeOut(animationSpec = tween(260))
                        },
                        label = "swing_minimal",
                    ) { key ->
                        if (key.isEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(CircleShape)
                                        .background(SwingTheme.colors.cardBorder.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = io.github.loje0611.tennisdoc.R.drawable.ic_neon_racket),
                                        contentDescription = "App Icon",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Ready for swing...",
                                    style = TextStyle(
                                        fontFamily = MichromaFont,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        lineHeight = 20.sp,
                                        letterSpacing = 1.5.sp,
                                        color = SwingTheme.colors.onBackground.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center,
                                        shadow = Shadow(color = SwingTheme.colors.onBackground.copy(alpha=0.6f), blurRadius = 24f)
                                    )
                                )
                            }
                        } else {
                            MinimalSwingHero(rawLabel = key)
                        }
                    }
                    
                    if (connectionState is BleConnectionState.Connected) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = formatTimer(sessionDuration),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = SwingTheme.colors.onBackground,
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$swingCount Swings",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 20.sp,
                                color = SwingTheme.colors.electricCyanSlice,
                            )
                        )
                    }
                }
                
                if (debugEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    if (w <= 0f || h <= 0f) return@detectTapGestures
                                    val col = ((offset.x / w) * 3f).toInt().coerceIn(0, 2)
                                    val row = ((offset.y / h) * 3f).toInt().coerceIn(0, 2)
                                    val type = debugSwingLabelForNinePatch(row, col) ?: return@detectTapGestures
                                    viewModel.simulateSwing(type)
                                }
                            },
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BleActionSection(
                    connectionState = connectionState,
                    permissionsGranted = permissionsState.allPermissionsGranted,
                    onScanConnect = { viewModel.scanAndConnect() },
                    onDisconnect = { viewModel.disconnect() },
                    onCancelScan = { setShowCancelScanDialog(true) },
                )
                if (!permissionsState.allPermissionsGranted) {
                    Text(
                        text = "알림·위치·블루투스 권한을 허용해 주세요. (백그라운드 분석 알림에 필요합니다)",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwingTheme.colors.danger,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PracticeAppHeader(
    connectionState: BleConnectionState,
    showDebugBadge: Boolean = false,
    onTitleClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SwingSense AI",
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onTitleClick() },
                style = TextStyle(
                    fontFamily = MichromaFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = SwingTheme.colors.onBackground,
                    letterSpacing = 2.sp
                ),
            )
            if (showDebugBadge) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "D",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = SwingTheme.colors.neonGreenTopspin,
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (connectionState !is BleConnectionState.Disconnected && connectionState !is BleConnectionState.Error) {
            val bgColor = if (connectionState is BleConnectionState.Connected)
                SwingTheme.colors.dotConnected.copy(alpha = 0.15f)
            else
                SwingTheme.colors.dotScanning.copy(alpha = 0.15f)

            val iconColor = if (connectionState is BleConnectionState.Connected)
                SwingTheme.colors.dotConnected.copy(alpha = 0.8f)
            else
                SwingTheme.colors.dotScanning.copy(alpha = 0.8f)

            Box(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(bgColor)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors, 
                        contentDescription = "센서 연결 상태",
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bleStatusSubtitle(connectionState),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = iconColor,
                        ),
                    )
                }
            }
        }
    }
}

private fun bleStatusSubtitle(state: BleConnectionState): String = when (state) {
    is BleConnectionState.Disconnected -> "Disconnected"
    is BleConnectionState.Scanning -> "Scanning..."
    is BleConnectionState.Connected -> "Sensor Connected"
    is BleConnectionState.Error -> when (state.reason) {
        BleConnectionState.ErrorReason.BluetoothOff -> "Bluetooth Off"
        BleConnectionState.ErrorReason.PermissionDenied -> "Permission Denied"
        BleConnectionState.ErrorReason.ScanFailed -> "Scan Failed"
        BleConnectionState.ErrorReason.ConnectionTimeout -> "Connection Timeout"
        BleConnectionState.ErrorReason.MaxReconnectReached -> "Reconnect Failed"
    }
}

@Composable
private fun MinimalSwingHero(rawLabel: String) {
    val lines = remember(rawLabel) { SwingLabelFormatter.linesForDisplay(rawLabel) }
    val accent = accentColorForSwingLabel(rawLabel)

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(0.9f),
        contentAlignment = Alignment.Center,
    ) {
        val line1Sp = (this.maxWidth.value * 0.16f).coerceIn(40f, 68f).sp
        val line2Sp = (this.maxWidth.value * 0.13f).coerceIn(32f, 52f).sp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            lines.forEachIndexed { index, line ->
                val size = if (index == 0) line1Sp else line2Sp
                val base = TextStyle(
                    fontFamily = MichromaFont,
                    fontWeight = FontWeight.Black,
                    fontSize = size,
                    lineHeight = (size.value * 1.05f).sp,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = line,
                    style = neonTextStyle(base, accent),
                    modifier = Modifier.wrapContentWidth(),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun BleActionSection(
    connectionState: BleConnectionState,
    permissionsGranted: Boolean,
    onScanConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelScan: () -> Unit,
) {
    when (connectionState) {
        is BleConnectionState.Disconnected, is BleConnectionState.Error -> {
            Button(
                onClick = onScanConnect,
                enabled = permissionsGranted,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(PillHeight)
                    .shadow(elevation = 24.dp, shape = CapsuleShape, spotColor = SwingTheme.colors.connectBlue, ambientColor = SwingTheme.colors.connectBlue),
                shape = CapsuleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Color.White.copy(alpha = 0.55f),
                ),
                contentPadding = PaddingValues(0.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        if (permissionsGranted) SwingTheme.colors.brushConnectButton 
                        else androidx.compose.ui.graphics.SolidColor(SwingTheme.colors.cardBorder)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Connect Sensor",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White,
                        ),
                    )
                }
            }
        }

        is BleConnectionState.Scanning -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(PillHeight)
                    .clip(CapsuleShape)
                    .background(SwingTheme.colors.scanningTrack)
                    .clickable { onCancelScan() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = SwingTheme.colors.dotScanning,
                        trackColor = SwingTheme.colors.cardBorder,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Scanning...",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = SwingTheme.colors.onBackground.copy(alpha = 0.85f),
                        ),
                    )
                }
            }
        }

        is BleConnectionState.Connected -> {
            Button(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(PillHeight)
                    .shadow(elevation = 28.dp, shape = CapsuleShape, spotColor = Color.Red, ambientColor = Color.Red),
                shape = CapsuleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(0.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(SwingTheme.colors.brushStopButton),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Stop Session",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White,
                        ),
                    )
                }
            }
        }
    }
}
