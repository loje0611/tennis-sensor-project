package com.example.swingsenseai.ui.settings

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.swingsenseai.session.SwingAnalysisSessionState
import com.example.swingsenseai.ui.theme.MichromaFont
import com.example.swingsenseai.ui.theme.SwingTheme

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
                    Text("라켓을 평평한 바닥에 가만히 내려놓은 상태에서 '시작'을 눌러주세요.")
                }
            },
            confirmButton = {
                if (step == CalibrationStep.IDLE) {
                    TextButton(
                        onClick = { viewModel.startAutoCalibration() }
                    ) {
                        Text(
                            "시작",
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
    }
}
