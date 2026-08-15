package io.github.loje0611.tennisdoc.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.loje0611.tennisdoc.session.SwingAnalysisSessionState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import io.github.loje0611.tennisdoc.feature.history.HistoryScreen
import io.github.loje0611.tennisdoc.feature.history.HistoryViewModel
import io.github.loje0611.tennisdoc.feature.history.SessionDetailScreen
import io.github.loje0611.tennisdoc.feature.history.SessionDetailViewModel

import io.github.loje0611.tennisdoc.feature.lab.replay.LabReplayScreen
import io.github.loje0611.tennisdoc.feature.lab.replay.LabReplayViewModel
import io.github.loje0611.tennisdoc.feature.lab.ui.LabScreen
import io.github.loje0611.tennisdoc.feature.lab.ui.LabViewModel

import io.github.loje0611.tennisdoc.ui.settings.DeveloperSettingsScreen
import io.github.loje0611.tennisdoc.ui.settings.DeveloperSettingsViewModel
import io.github.loje0611.tennisdoc.ui.settings.SettingsScreen
import io.github.loje0611.tennisdoc.ui.settings.SettingsViewModel
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // 초기 구성 시 route가 잠깐 null이면 바가 사라지지 않도록 처리
    val showBottomBar =
        currentRoute == null ||
            currentRoute == AppRoutes.LAB ||
            currentRoute == AppRoutes.HISTORY ||
            currentRoute == AppRoutes.SETTINGS ||
            currentRoute == AppRoutes.ENGINEERING_MODE

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SwingTheme.colors.background),
        containerColor = SwingTheme.colors.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SwingTheme.colors.cardSurface.copy(alpha=0.9f),
                    tonalElevation = 8.dp,
                ) {
                    NavigationBarItem(
                        selected = currentRoute == AppRoutes.LAB,
                        onClick = {
                            navController.navigate(AppRoutes.LAB) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Sensors,
                                contentDescription = "Lab",
                                modifier = Modifier.shadow(
                                    elevation = if (currentRoute == AppRoutes.LAB) 12.dp else 0.dp,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    spotColor = SwingTheme.colors.electricCyanSlice,
                                    ambientColor = SwingTheme.colors.electricCyanSlice
                                )
                            )
                        },
                        label = {
                            Text(
                                "Lab",
                                fontWeight = if (currentRoute == AppRoutes.LAB) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SwingTheme.colors.electricCyanSlice,
                            selectedTextColor = SwingTheme.colors.electricCyanSlice,
                            unselectedIconColor = SwingTheme.colors.subGray,
                            unselectedTextColor = SwingTheme.colors.subGray,
                            indicatorColor = Color.Transparent
                        ),
                    )

                    NavigationBarItem(
                        selected = currentRoute == AppRoutes.HISTORY,
                        onClick = {
                            navController.navigate(AppRoutes.HISTORY) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "History",
                                modifier = Modifier.shadow(
                                    elevation = if (currentRoute == AppRoutes.HISTORY) 12.dp else 0.dp,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    spotColor = SwingTheme.colors.electricCyanSlice,
                                    ambientColor = SwingTheme.colors.electricCyanSlice
                                )
                            )
                        },
                        label = {
                            Text(
                                "History",
                                fontWeight = if (currentRoute == AppRoutes.HISTORY) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SwingTheme.colors.electricCyanSlice,
                            selectedTextColor = SwingTheme.colors.electricCyanSlice,
                            unselectedIconColor = SwingTheme.colors.subGray,
                            unselectedTextColor = SwingTheme.colors.subGray,
                            indicatorColor = Color.Transparent
                        ),
                    )
                    NavigationBarItem(
                        selected = currentRoute == AppRoutes.SETTINGS,
                        onClick = {
                            navController.navigate(AppRoutes.SETTINGS) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.shadow(
                                    elevation = if (currentRoute == AppRoutes.SETTINGS) 12.dp else 0.dp,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    spotColor = SwingTheme.colors.neonPurpleSettings,
                                    ambientColor = SwingTheme.colors.neonPurpleSettings
                                )
                            )
                        },
                        label = {
                            Text(
                                "Settings",
                                fontWeight = if (currentRoute == AppRoutes.SETTINGS) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SwingTheme.colors.neonPurpleSettings,
                            selectedTextColor = SwingTheme.colors.neonPurpleSettings,
                            unselectedIconColor = SwingTheme.colors.subGray,
                            unselectedTextColor = SwingTheme.colors.subGray,
                            indicatorColor = Color.Transparent
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.LAB,
            modifier = Modifier
                .fillMaxSize()
                .background(SwingTheme.colors.background),
        ) {

            composable(AppRoutes.LAB) {
                val labViewModel: LabViewModel = hiltViewModel()
                LabScreen(
                    viewModel = labViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            
            composable(AppRoutes.HISTORY) {
                val historyViewModel: HistoryViewModel = hiltViewModel()
                val debugModeEnabled by SwingAnalysisSessionState.debugModeEnabled.collectAsStateWithLifecycle()
                HistoryScreen(
                    onNavigateToSessionDetail = { sessionId ->
                        navController.navigate(AppRoutes.sessionDetail(sessionId))
                    },
                    viewModel = historyViewModel,
                    debugModeEnabled = debugModeEnabled,
                    contentPadding = innerPadding,
                )
            }
            composable(AppRoutes.SETTINGS) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    contentPadding = innerPadding,
                    onNavigateToDeveloperSettings = {
                        navController.navigate(AppRoutes.ENGINEERING_MODE) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppRoutes.ENGINEERING_MODE) {
                val devViewModel: DeveloperSettingsViewModel = hiltViewModel()
                DeveloperSettingsScreen(
                    viewModel = devViewModel,
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = AppRoutes.SESSION_DETAIL,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                ),
            ) {
                val sessionDetailViewModel: SessionDetailViewModel = hiltViewModel()
                SessionDetailScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = sessionDetailViewModel,
                    onNavigateToReplay = { sessionId, recordId ->
                        navController.navigate(AppRoutes.labReplay(sessionId, recordId))
                    },
                    contentPadding = innerPadding,
                )
            }
            composable(
                route = AppRoutes.LAB_REPLAY,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("recordId") { type = NavType.LongType },
                ),
            ) {
                val replayViewModel: LabReplayViewModel = hiltViewModel()
                LabReplayScreen(
                    viewModel = replayViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
