package io.github.loje0611.tennisdoc.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.github.loje0611.tennisdoc.session.SwingAnalysisSessionState
import io.github.loje0611.tennisdoc.core.ui.theme.MichromaFont
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.loje0611.tennisdoc.core.ui.coach.CoachToneSelector
import io.github.loje0611.tennisdoc.core.model.LlmProvider
import androidx.compose.material3.ExperimentalMaterial3Api

private fun bleRuntimePermissions(): List<String> = buildList {
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
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onNavigateToDeveloperSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    var showCalibrationDialog by remember { mutableStateOf(false) }
    val step by viewModel.calibrationStep.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isDebugMode by SwingAnalysisSessionState.debugModeEnabled.collectAsStateWithLifecycle()

    val permissionsState = rememberMultiplePermissionsState(bleRuntimePermissions())

    LaunchedEffect(Unit) {
        viewModel.calibrationResultEvent.collect { success ->
            if (success) {
                Toast.makeText(context, "Sensor Calibration이 성공적으로 완료되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "응답 없음: 연결 혹은 전송에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
            showCalibrationDialog = false
        }
    }

    if (showCalibrationDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (step == CalibrationStep.IDLE) {
                    showCalibrationDialog = false 
                }
            },
            containerColor = SwingTheme.colors.cardSurface,
            titleContentColor = SwingTheme.colors.onBackground,
            textContentColor = SwingTheme.colors.onBackgroundVariant,
            title = {
                Text(
                    text = "센서 영점 조절",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (step != CalibrationStep.IDLE) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            color = SwingTheme.colors.neonPurpleSettings,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (step == CalibrationStep.CONNECTING) "센서를 찾고 연결하는 중..." else "영점 조절 중... 라켓을 바닥에 가만히 두세요.",
                            color = SwingTheme.colors.neonPurpleSettings,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Column {
                        Text("라켓을 평평한 바닥에 가만히 내려놓은 상태에서 '시작'을 눌러주세요.")
                        if (!permissionsState.allPermissionsGranted) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "블루투스 및 위치 권한이 필요합니다.",
                                color = SwingTheme.colors.danger,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (step == CalibrationStep.IDLE) {
                    TextButton(
                        onClick = {
                            if (permissionsState.allPermissionsGranted) {
                                viewModel.startAutoCalibration()
                            } else {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                        }
                    ) {
                        Text(
                            if (permissionsState.allPermissionsGranted) "시작" else "권한 허용",
                            color = SwingTheme.colors.neonPurpleSettings,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                if (step == CalibrationStep.IDLE) {
                    TextButton(
                        onClick = { showCalibrationDialog = false }
                    ) {
                        Text("취소", color = SwingTheme.colors.subGray)
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.cancelCalibration()
                            showCalibrationDialog = false
                        }
                    ) {
                        Text("중단", color = SwingTheme.colors.stopBg)
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SwingTheme.colors.background)
            .padding(contentPadding)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = MichromaFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = SwingTheme.colors.onBackground,
            ),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable {
                    SwingAnalysisSessionState.onDebugActivationAreaTap()
                },
        )

        // Theme Toggle MenuItem
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = SwingTheme.colors.cardBorder.copy(alpha = 0.5f),
                    spotColor = SwingTheme.colors.cardBorder.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(SwingTheme.colors.cardSurface)
                .border(
                    width = 0.5.dp,
                    color = SwingTheme.colors.cardBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    viewModel.toggleDarkMode()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dark Mode",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MichromaFont,
                        fontWeight = FontWeight.Bold,
                        color = SwingTheme.colors.onBackground,
                        fontSize = 16.sp
                    ),
                )
                
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { viewModel.toggleDarkMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SwingTheme.colors.neonPurpleSettings,
                        checkedTrackColor = SwingTheme.colors.neonPurpleSettings.copy(alpha=0.2f)
                    )
                )
            }
        }

        // Calibration MenuItem
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.2f),
                    ambientColor = SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.05f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(SwingTheme.colors.cardSurface)
                .border(
                    width = 0.5.dp,
                    color = SwingTheme.colors.cardBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    showCalibrationDialog = true
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sensor Calibration",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MichromaFont,
                        fontWeight = FontWeight.Bold,
                        color = SwingTheme.colors.onBackground,
                        fontSize = 16.sp
                    ),
                )
                
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SwingTheme.colors.progressTrack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Calibration",
                        tint = SwingTheme.colors.neonPurpleSettings,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Developer Calibration MenuItem (debug mode only)
        if (isDebugMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.3f),
                        ambientColor = SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.1f)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(SwingTheme.colors.cardSurface)
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.6f),
                                SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.2f),
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onNavigateToDeveloperSettings() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Engineering Mode",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = MichromaFont,
                                fontWeight = FontWeight.Bold,
                                color = SwingTheme.colors.neonPurpleSettings,
                                fontSize = 14.sp
                            ),
                        )
                        Text(
                            text = "Threshold tuning & live console",
                            color = SwingTheme.colors.subGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Developer",
                            tint = SwingTheme.colors.neonPurpleSettings,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        AiCoachSettingsSection(viewModel)
        VideoStorageSettingsSection(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachSettingsSection(viewModel: SettingsViewModel) {
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val llmProvider by viewModel.llmProvider.collectAsStateWithLifecycle()
    val defaultCoachTone by viewModel.defaultCoachTone.collectAsStateWithLifecycle()
    val testState by viewModel.apiKeyTestState.collectAsStateWithLifecycle()

    var isPasswordVisible by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(geminiApiKey ?: "") }
    var providerExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(geminiApiKey) {
        apiKeyInput = geminiApiKey ?: ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = SwingTheme.colors.cardBorder.copy(alpha = 0.5f),
                spotColor = SwingTheme.colors.cardBorder.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(SwingTheme.colors.cardSurface)
            .border(
                width = 0.5.dp,
                color = SwingTheme.colors.cardBorder,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🤖 AI 코치 설정",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SwingTheme.colors.onBackground
                )
            )

            // Provider Selector
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = !providerExpanded },
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = when (llmProvider) {
                        LlmProvider.GEMINI -> "Google Gemini Flash (권장)"
                        LlmProvider.LOCAL_RULE_ONLY -> "오프라인 룰 엔진 (로컬 전용)"
                        LlmProvider.MOCK -> "가상 Mock 코치"
                        LlmProvider.OPENAI -> "OpenAI GPT (실험적)"
                    },
                    onValueChange = {},
                    label = { Text("AI 프로바이더") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SwingTheme.colors.neonPurpleSettings,
                        focusedLabelColor = SwingTheme.colors.neonPurpleSettings
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Google Gemini Flash (권장)") },
                        onClick = {
                            viewModel.saveLlmProvider(LlmProvider.GEMINI)
                            providerExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("오프라인 룰 엔진 (로컬 전용)") },
                        onClick = {
                            viewModel.saveLlmProvider(LlmProvider.LOCAL_RULE_ONLY)
                            providerExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("가상 Mock 코치") },
                        onClick = {
                            viewModel.saveLlmProvider(LlmProvider.MOCK)
                            providerExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("OpenAI GPT (실험적)") },
                        onClick = {
                            viewModel.saveLlmProvider(LlmProvider.OPENAI)
                            providerExpanded = false
                        }
                    )
                }
            }

            // API Key Input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { 
                        apiKeyInput = it
                        viewModel.saveGeminiApiKey(it)
                    },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.weight(1f),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (isPasswordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(imageVector = image, contentDescription = if (isPasswordVisible) "Hide password" else "Show password")
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SwingTheme.colors.neonPurpleSettings,
                        focusedLabelColor = SwingTheme.colors.neonPurpleSettings
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = { viewModel.testGeminiApiKey(apiKeyInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SwingTheme.colors.neonPurpleSettings
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("연결 테스트")
                }
            }

            Text(
                text = "Google AI Studio에서 무료로 발급받은 API Key를 입력하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = SwingTheme.colors.subGray
            )

            // Test status
            when (testState) {
                is ApiKeyTestStatus.Testing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = SwingTheme.colors.neonPurpleSettings,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = " 연결 확인 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwingTheme.colors.onBackgroundVariant
                        )
                    }
                }
                is ApiKeyTestStatus.Success -> {
                    Box(
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color(0xFFDCFCE7), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✔ 연결 성공",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color(0xFF16A34A)
                        )
                    }
                }
                is ApiKeyTestStatus.Error -> {
                    Box(
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✖ ${(testState as ApiKeyTestStatus.Error).message}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color(0xFFDC2626)
                        )
                    }
                }
                ApiKeyTestStatus.Idle -> {}
            }

            // Coach Tone Selector
            Text(
                text = "기본 코칭 톤",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SwingTheme.colors.onBackground
                )
            )
            CoachToneSelector(
                selectedTone = defaultCoachTone,
                onToneSelected = { viewModel.saveDefaultCoachTone(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoStorageSettingsSection(viewModel: SettingsViewModel) {
    val autoSaveVideoEnabled by viewModel.autoSaveVideoEnabled.collectAsStateWithLifecycle()
    val videoRetentionOption by viewModel.videoRetentionOption.collectAsStateWithLifecycle()
    val savedVideoCount by viewModel.savedVideoCount.collectAsStateWithLifecycle()
    val usedStorageText by viewModel.usedStorageText.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showClearDialog by remember { mutableStateOf(false) }
    var retentionExpanded by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = SwingTheme.colors.cardSurface,
            titleContentColor = SwingTheme.colors.onBackground,
            textContentColor = SwingTheme.colors.onBackgroundVariant,
            title = {
                Text("비디오 캐시 삭제", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("저장된 모든 스윙 영상($savedVideoCount 개)을 삭제하시겠습니까? 삭제된 영상은 복구할 수 없으며, 기존 세션 기록에는 텍스트와 데이터만 남게 됩니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearVideoCache {
                            Toast.makeText(context, "스윙 영상 캐시가 모두 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                        showClearDialog = false
                    }
                ) {
                    Text("삭제", color = SwingTheme.colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("취소", color = SwingTheme.colors.subGray)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = SwingTheme.colors.cardBorder.copy(alpha = 0.5f),
                spotColor = SwingTheme.colors.cardBorder.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(SwingTheme.colors.cardSurface)
            .border(
                width = 0.5.dp,
                color = SwingTheme.colors.cardBorder,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📹 스윙 영상 & 저장소 설정",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SwingTheme.colors.onBackground
                )
            )

            // Auto Save Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "스윙 영상 자동 저장",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = SwingTheme.colors.onBackground
                        )
                    )
                    Text(
                        text = "스윙 감지 시 2초 비디오 클립을 저장합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwingTheme.colors.subGray
                    )
                }
                Switch(
                    checked = autoSaveVideoEnabled,
                    onCheckedChange = { viewModel.toggleAutoSaveVideo(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SwingTheme.colors.neonPurpleSettings,
                        checkedTrackColor = SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.2f)
                    )
                )
            }

            // Retention Option Selector
            Column(
                modifier = Modifier.fillMaxWidth().then(
                    if (autoSaveVideoEnabled) Modifier else Modifier.background(SwingTheme.colors.cardSurface.copy(alpha = 0.5f))
                )
            ) {
                ExposedDropdownMenuBox(
                    expanded = retentionExpanded && autoSaveVideoEnabled,
                    onExpandedChange = { if (autoSaveVideoEnabled) retentionExpanded = !retentionExpanded },
                ) {
                    OutlinedTextField(
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        value = "${videoRetentionOption.displayName} (${videoRetentionOption.approximateSize})",
                        onValueChange = {},
                        label = { Text("최대 보관 클립 수") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = retentionExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SwingTheme.colors.neonPurpleSettings,
                            focusedLabelColor = SwingTheme.colors.neonPurpleSettings
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = autoSaveVideoEnabled
                    )
                    ExposedDropdownMenu(
                        expanded = retentionExpanded,
                        onDismissRequest = { retentionExpanded = false },
                    ) {
                        io.github.loje0611.tennisdoc.core.model.VideoRetentionOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.displayName} (${option.approximateSize})") },
                                onClick = {
                                    viewModel.selectVideoRetentionOption(option)
                                    retentionExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Storage Info & Clear Cache
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "저장된 비디오: $savedVideoCount 개 / $usedStorageText",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = SwingTheme.colors.onBackground
                )

                androidx.compose.material3.OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SwingTheme.colors.danger),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SwingTheme.colors.danger)
                ) {
                    Text("🗑️ 비디오 캐시 전체 삭제")
                }
            }
        }
    }
}

