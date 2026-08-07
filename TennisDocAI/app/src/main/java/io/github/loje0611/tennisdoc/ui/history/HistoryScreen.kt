package io.github.loje0611.tennisdoc.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import io.github.loje0611.tennisdoc.navigation.AppRoutes
import io.github.loje0611.tennisdoc.session.SwingAnalysisSessionState
import io.github.loje0611.tennisdoc.core.ui.theme.MichromaFont
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme
import io.github.loje0611.tennisdoc.core.data.db.entity.SwingSessionEntity

@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val isMockInserting by viewModel.mockInsertInProgress.collectAsStateWithLifecycle()
    val isDebugMode by SwingAnalysisSessionState.debugModeEnabled.collectAsState()

    Scaffold(
        modifier = Modifier
            .padding(contentPadding)
            .background(SwingTheme.colors.background),
        containerColor = SwingTheme.colors.background,
        floatingActionButton = {
            if (isDebugMode) {
                FloatingActionButton(
                    onClick = { viewModel.insertMockSessionData() },
                    containerColor = SwingTheme.colors.cardSurface,
                    contentColor = SwingTheme.colors.neonPurpleSettings,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.semantics { contentDescription = "Mock 세션 데이터 생성" }.shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.4f),
                        ambientColor = SwingTheme.colors.neonPurpleSettings.copy(alpha = 0.15f),
                    ),
                ) {
                    if (isMockInserting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = SwingTheme.colors.neonPurpleSettings,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Mock",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SwingTheme.colors.neonPurpleSettings,
                        )
                    }
                }
            }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SwingTheme.colors.background)
                .padding(scaffoldPadding)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = MichromaFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = SwingTheme.colors.onBackground,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(items = sessions, key = { it.sessionId }) { session ->
                val sessionDesc = "${SwingSessionEntity.formatSessionName(session.startTime)}, " +
                    "${session.totalSwingCount} swings, ${formatDurationMillis(session.durationMillis)}"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics(mergeDescendants = true) { contentDescription = sessionDesc }
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = SwingTheme.colors.electricCyanSlice.copy(alpha = 0.2f),
                            ambientColor = SwingTheme.colors.electricCyanSlice.copy(alpha = 0.05f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(SwingTheme.colors.cardSurface)
                        .border(
                            width = 0.5.dp,
                            color = SwingTheme.colors.cardBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            navController.navigate(AppRoutes.sessionDetail(session.sessionId))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = SwingSessionEntity.formatSessionName(session.startTime),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = MichromaFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SwingTheme.colors.onBackground,
                                    fontSize = 16.sp
                                ),
                            )
                            Text(
                                text = "${session.totalSwingCount} swings · ${formatDurationMillis(session.durationMillis)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    color = SwingTheme.colors.subGray,
                                    fontSize = 13.sp
                                ),
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SwingTheme.colors.progressTrack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "View Details",
                                tint = SwingTheme.colors.subGray
                            )
                        }
                    }
                }
            }
        }
    }
}
