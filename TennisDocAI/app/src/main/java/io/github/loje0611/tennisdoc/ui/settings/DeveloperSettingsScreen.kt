package io.github.loje0611.tennisdoc.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.loje0611.tennisdoc.core.ui.theme.MichromaFont
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme
import kotlinx.coroutines.delay

@Composable
fun DeveloperSettingsScreen(
    viewModel: DeveloperSettingsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsStateWithLifecycle()
    val rawSwingData by viewModel.lastRawSwingData.collectAsStateWithLifecycle()
    val mockConnected by viewModel.mockConnected.collectAsStateWithLifecycle()
    val pipelineRunning by viewModel.pipelineRunning.collectAsStateWithLifecycle()
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.exportEvent.collect { result ->
            exporting = false
            when (result) {
                is DeveloperSettingsViewModel.ExportResult.Success -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, result.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export Swing Data"))
                }
                is DeveloperSettingsViewModel.ExportResult.Error -> {
                    Toast.makeText(context, "Export failed: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SwingTheme.colors.background)
            .padding(contentPadding),
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SwingTheme.colors.onBackground,
                )
            }
            Text(
                text = "Engineering Mode",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = MichromaFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = SwingTheme.colors.onBackground,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.resetToDefaults() }) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = SwingTheme.colors.stopBg,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Reset",
                    color = SwingTheme.colors.stopBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }

        // ── Scrollable Content ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ════════════════════════════════════════════════════════════
            // Section 1: Mock BLE Simulator
            // ════════════════════════════════════════════════════════════
            SectionLabel("MOCK BLE SIMULATOR")

            // Mock Connection Toggle
            val realBleRunning = pipelineRunning && !mockConnected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SwingTheme.colors.cardSurface)
                    .border(
                        width = if (mockConnected) 1.dp else 0.5.dp,
                        color = if (mockConnected) SwingTheme.colors.dotConnected
                        else SwingTheme.colors.cardBorder,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Mock BLE Connection",
                        color = SwingTheme.colors.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    if (realBleRunning) {
                        Text(
                            "Real BLE session active",
                            color = SwingTheme.colors.danger,
                            fontSize = 11.sp,
                        )
                    } else if (mockConnected) {
                        Text(
                            "Virtual session running",
                            color = SwingTheme.colors.dotConnected,
                            fontSize = 11.sp,
                        )
                    }
                }
                Switch(
                    checked = mockConnected,
                    onCheckedChange = { viewModel.toggleMockConnection(it) },
                    enabled = !realBleRunning,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SwingTheme.colors.dotConnected,
                        checkedTrackColor = SwingTheme.colors.dotConnected.copy(alpha = 0.25f),
                    ),
                )
            }

            // Mock Swing Buttons
            if (mockConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                MockSwingButtonGrid(
                    onSwing = { viewModel.triggerMockSwing(it) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ════════════════════════════════════════════════════════════
            // Section 2: Calibration Tuning
            // ════════════════════════════════════════════════════════════
            SectionLabel("CALIBRATION TUNING")

            CalibrationStepper(
                label = "Volley Accel Threshold",
                unit = "g",
                value = config.volleyAccelThreshold,
                range = 0.5f..10f,
                step = 0.1f,
                onValueChange = { viewModel.updateVolleyAccelThreshold(it) },
                formatDisplay = { "%.2f".format(it) },
            )

            CalibrationStepper(
                label = "Volley Max Duration",
                unit = "ms",
                value = config.volleyMaxDurationMs.toFloat(),
                range = 50f..1000f,
                step = 10f,
                onValueChange = { viewModel.updateVolleyMaxDurationMs(it.toInt()) },
                formatDisplay = { "%d".format(it.toInt()) },
            )

            CalibrationStepper(
                label = "Gyro Follow-Through",
                unit = "dps",
                value = config.gyroFollowThroughThreshold,
                range = 50f..2500f,
                step = 50f,
                onValueChange = { viewModel.updateGyroFollowThroughThreshold(it) },
                formatDisplay = { "%.0f".format(it) },
            )

            CalibrationStepper(
                label = "Power Max Norm",
                unit = "m/s\u00B2",
                value = config.powerMaxNormalization,
                range = 10f..100f,
                step = 1f,
                onValueChange = { viewModel.updatePowerMaxNormalization(it) },
                formatDisplay = { "%.1f".format(it) },
            )

            CalibrationStepper(
                label = "Spin Max Norm",
                unit = "dps",
                value = config.spinMaxNormalization,
                range = 500f..5000f,
                step = 50f,
                onValueChange = { viewModel.updateSpinMaxNormalization(it) },
                formatDisplay = { "%.0f".format(it) },
            )

            CalibrationStepper(
                label = "Smoothness Worst Var",
                unit = "var",
                value = config.smoothnessWorstVariance,
                range = 5f..500f,
                step = 5f,
                onValueChange = { viewModel.updateSmoothnessWorstVariance(it) },
                formatDisplay = { "%.1f".format(it) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ════════════════════════════════════════════════════════════
            // Section 3: Tools
            // ════════════════════════════════════════════════════════════
            SectionLabel("TOOLS")

            // Export CSV Button
            Button(
                onClick = {
                    exporting = true
                    viewModel.exportCsv()
                },
                enabled = !exporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SwingTheme.colors.electricCyanSlice,
                    contentColor = Color.Black,
                    disabledContainerColor = SwingTheme.colors.electricCyanSlice.copy(alpha = 0.4f),
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (exporting) "Exporting..." else "Export CSV & Share",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Live Debug Console (fixed at bottom) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "LIVE DEBUG CONSOLE",
                color = SwingTheme.colors.neonPurpleSettings,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A0A0F))
                    .border(1.dp, SwingTheme.colors.cardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                val consolScroll = rememberScrollState()
                val hScroll = rememberScrollState()

                if (rawSwingData.isBlank()) {
                    Text(
                        text = "Waiting for swing data...",
                        color = Color(0xFF555566),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        text = rawSwingData,
                        color = Color(0xFF39FF14),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .verticalScroll(consolScroll)
                            .horizontalScroll(hScroll),
                    )
                }
            }
        }
    }
}

// ── Section Label ────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = SwingTheme.colors.subGray,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

// ── Mock Swing Button Grid ───────────────────────────────────────────────

private data class MockSwingEntry(
    val label: String,
    val key: String,
    val brush: @Composable () -> Brush,
)

@Composable
private fun MockSwingButtonGrid(onSwing: (String) -> Unit) {
    val entries = listOf(
        MockSwingEntry("FH\nTopspin", "forehand topspin") { SwingTheme.colors.brushTopspin },
        MockSwingEntry("BH\nTopspin", "backhand topspin") { SwingTheme.colors.brushTopspin },
        MockSwingEntry("FH\nSlice", "forehand slice") { SwingTheme.colors.brushSlice },
        MockSwingEntry("BH\nSlice", "backhand slice") { SwingTheme.colors.brushSlice },
        MockSwingEntry("FH\nVolley", "forehand volley") { SwingTheme.colors.brushVolley },
        MockSwingEntry("BH\nVolley", "backhand volley") { SwingTheme.colors.brushVolley },
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in entries.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (entry in row) {
                    Button(
                        onClick = { onSwing(entry.key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(entry.brush(), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = entry.label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Stepper Control ──────────────────────────────────────────────────────

@Composable
private fun CalibrationStepper(
    label: String,
    unit: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    formatDisplay: (Float) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SwingTheme.colors.cardSurface)
            .border(0.5.dp, SwingTheme.colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            color = SwingTheme.colors.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatableStepButton(
                onClick = {
                    val clamped = (value - step).coerceIn(range)
                    onValueChange(clamped)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = SwingTheme.colors.onBackground,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatDisplay(value),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = SwingTheme.colors.neonPurpleSettings,
                )
                Text(
                    text = unit,
                    color = SwingTheme.colors.subGray,
                    fontSize = 12.sp,
                )
            }

            RepeatableStepButton(
                onClick = {
                    val clamped = (value + step).coerceIn(range)
                    onValueChange(clamped)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = SwingTheme.colors.onBackground,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDisplay(range.start),
                color = SwingTheme.colors.subGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "step: ${formatDisplay(step)}",
                color = SwingTheme.colors.subGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = formatDisplay(range.endInclusive),
                color = SwingTheme.colors.subGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ── Repeatable Step Button (long-press to auto-repeat) ───────────────────

private const val INITIAL_REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 60L

@Composable
private fun RepeatableStepButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            currentOnClick()
            delay(INITIAL_REPEAT_DELAY_MS)
            while (true) {
                currentOnClick()
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    val bgColor = if (isPressed) {
        SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.25f)
    } else {
        SwingTheme.colors.progressTrack
    }

    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
