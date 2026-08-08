package io.github.loje0611.tennisdoc.feature.history

import androidx.hilt.navigation.compose.hiltViewModel
import android.graphics.BlurMaskFilter
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.loje0611.tennisdoc.core.ui.displayCategoryTitle
import io.github.loje0611.tennisdoc.core.ui.formatDurationMillis
import io.github.loje0611.tennisdoc.core.ui.progressBrushForCategoryKey
import io.github.loje0611.tennisdoc.core.ui.progressColorForCategoryKey
import io.github.loje0611.tennisdoc.core.ui.theme.MichromaFont
import io.github.loje0611.tennisdoc.core.ui.theme.SwingTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        DeleteSessionDialog(
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteSession {
                    Toast.makeText(context, "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    // ── 바텀 시트: 표시 여부를 로컬 Compose 상태로 관리 ──
    // ViewModel StateFlow 변경이 이 boolean에 영향을 주지 않으므로
    // 캐시 업데이트 시에도 시트가 닫히지 않는다.
    val categories = remember(state.breakdown) { state.breakdown.map { it.categoryKey } }
    var showSheet by remember { mutableStateOf(false) }
    var targetPageIdx by remember { mutableIntStateOf(0) }

    if (showSheet && categories.isNotEmpty()) {
        AnalysisBottomSheet(
            categories = categories,
            targetPageIdx = targetPageIdx,
            analysisCache = state.analysisCache,
            onPageSettled = { key -> viewModel.ensureCategoryLoaded(key) },
            onDismiss = { showSheet = false },
        )
    }

    // ── 메인 대시보드 ──
    Scaffold(
        modifier = Modifier.padding(contentPadding),
        containerColor = SwingTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.session?.let { "${it.sessionName}" } ?: "Session",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.ExtraBold,
                            color = SwingTheme.colors.onBackground,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SwingTheme.colors.onBackground)
                    }
                },
                actions = {
                    if (!state.notFound && !state.loading && state.session != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = SwingTheme.colors.danger)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SwingTheme.colors.background,
                    navigationIconContentColor = SwingTheme.colors.onBackground,
                    titleContentColor = SwingTheme.colors.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).background(SwingTheme.colors.background),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = SwingTheme.colors.neonGreenTopspin) }
            }
            state.notFound -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).background(SwingTheme.colors.background),
                    contentAlignment = Alignment.Center,
                ) { Text("세션을 찾을 수 없습니다.", color = SwingTheme.colors.subGray, fontFamily = FontFamily.SansSerif) }
            }
            else -> {
                val session = state.session ?: return@Scaffold
                val totalSwingsF = session.totalSwingCount.coerceAtLeast(1).toFloat()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(SwingTheme.colors.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${session.totalSwingCount} Total Swings",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = MichromaFont, fontWeight = FontWeight.ExtraBold,
                            fontSize = 44.sp, lineHeight = 48.sp, color = SwingTheme.colors.onBackground,
                            textAlign = TextAlign.Center, letterSpacing = 1.sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Duration · ${formatDurationMillis(session.durationMillis)}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif, color = SwingTheme.colors.subGray),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "구종을 선택하면 상세 분석을 볼 수 있습니다",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif, color = SwingTheme.colors.subGray.copy(alpha = 0.6f)),
                    )
                    Spacer(modifier = Modifier.height(40.dp))

                    state.breakdown.forEachIndexed { index, row ->
                        val fraction = row.count.toFloat() / totalSwingsF
                        val percentage = round((fraction * 100.0).toDouble()).toInt()
                        val barColor = progressColorForCategoryKey(row.categoryKey)
                        val barBrush = progressBrushForCategoryKey(row.categoryKey)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable {
                                    targetPageIdx = index
                                    viewModel.preloadAllCategories(categories)
                                    showSheet = true
                                }
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                NeonProgressBar(
                                    progress = fraction.coerceIn(0f, 1f), brush = barBrush,
                                    glowColor = barColor, modifier = Modifier.weight(1f).height(12.dp),
                                    label = displayCategoryTitle(row.categoryKey),
                                )
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(
                                    text = "$percentage% · ${row.count}회",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = MichromaFont, fontWeight = FontWeight.Bold,
                                        color = SwingTheme.colors.onBackground, fontSize = 14.sp,
                                    ),
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = displayCategoryTitle(row.categoryKey),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, color = SwingTheme.colors.subGray,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 분석 바텀 시트 (독립 Composable) ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisBottomSheet(
    categories: List<String>,
    targetPageIdx: Int,
    analysisCache: Map<String, CategoryAnalysisData>,
    onPageSettled: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pagerState = rememberPagerState(
        initialPage = targetPageIdx,
        pageCount = { categories.size },
    )

    // 외부에서 targetPageIdx가 바뀌면 (다른 행 클릭) 해당 페이지로 점프
    LaunchedEffect(targetPageIdx) {
        if (pagerState.currentPage != targetPageIdx) {
            pagerState.scrollToPage(targetPageIdx)
        }
    }

    // 페이지가 안착할 때마다 해당 구종 데이터 로드
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val key = categories.getOrNull(page) ?: return@collect
                onPageSettled(key)
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SwingTheme.colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SwingTheme.colors.cardBorder),
            )
        },
    ) {
        ModalBottomSheetDialogSystemBarsEffect()
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            key = { categories[it] },
        ) { page ->
            val catKey = categories[page]
            val analysis = analysisCache[catKey]
            AnalysisSheetPage(
                categoryKey = catKey,
                analysis = analysis,
                pageCount = categories.size,
                currentPage = pagerState.currentPage,
            )
        }
    }
}

/**
 * [ModalBottomSheet]의 ComponentDialog 윈도우만 조정한다.
 * Material3가 contentColor 기준으로 켜는 Light status bar(검정 아이콘)를 덮어쓰기 위해,
 * 매 프레임 [SideEffect]로 밝은 아이콘(dark appearance)을 유지한다.
 */
@Composable
private fun ModalBottomSheetDialogSystemBarsEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.findDialogWindowFromProvider() ?: return@DisposableEffect onDispose {}
        val prevStatus = window.statusBarColor
        val prevNav = window.navigationBarColor

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        onDispose {
            if (view.isAttachedToWindow) {
                window.statusBarColor = prevStatus
                window.navigationBarColor = prevNav
            }
        }
    }
    SideEffect {
        val window = view.findDialogWindowFromProvider() ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

private fun View.findDialogWindowFromProvider(): Window? {
    var p: ViewParent? = parent
    while (p != null) {
        if (p is DialogWindowProvider) return p.window
        p = (p as? View)?.parent
    }
    return null
}

// ── 바텀 시트 한 페이지 ──────────────────────────────────────────────────────

@Composable
private fun AnalysisSheetPage(
    categoryKey: String,
    analysis: CategoryAnalysisData?,
    pageCount: Int,
    currentPage: Int,
) {
    val metrics = analysis?.metrics
    val history = analysis?.historyMetrics
    val comment = analysis?.coachingComment ?: ""
    val loading = analysis?.loading != false

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${displayCategoryTitle(categoryKey)}\nAnalysis",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
                color = SwingTheme.colors.neonPurpleSettings, letterSpacing = 1.sp,
                lineHeight = 28.sp
            ),
        )

        if (pageCount > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                repeat(pageCount) { idx ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (idx == currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (idx == currentPage) SwingTheme.colors.neonPurpleSettings else SwingTheme.colors.cardBorder),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 300.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SwingTheme.colors.neonPurpleSettings, modifier = Modifier.size(32.dp))
            }
            return@Column
        }

        if (metrics == null) {
            if (comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(comment, color = SwingTheme.colors.subGray, fontFamily = FontFamily.SansSerif, textAlign = TextAlign.Center)
            }
            return@Column
        }

        HexagonalRadarChart(
            metrics = metrics, historyMetrics = history,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )

        if (history != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val historyGray = SwingTheme.colors.subGray
                val labelColor = SwingTheme.colors.onBackground.copy(alpha=0.6f)
                val neonPurple = SwingTheme.colors.neonPurpleSettings
                Canvas(modifier = Modifier.size(10.dp)) { drawCircle(neonPurple) }
                Spacer(modifier = Modifier.width(5.dp))
                Text("Today", style = MaterialTheme.typography.labelSmall.copy(color = labelColor, fontFamily = FontFamily.SansSerif))
                Spacer(modifier = Modifier.width(16.dp))
                Canvas(modifier = Modifier.size(10.dp)) { drawCircle(historyGray) }
                Spacer(modifier = Modifier.width(5.dp))
                Text("All-time Avg", style = MaterialTheme.typography.labelSmall.copy(color = labelColor, fontFamily = FontFamily.SansSerif))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        DeltaSummaryChips(current = metrics, history = history, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))

        if (comment.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SwingTheme.colors.cardSurface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "💬  Coach's Note",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                            color = SwingTheme.colors.neonPurpleSettings, letterSpacing = 1.sp,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
                            fontStyle = FontStyle.Italic, color = SwingTheme.colors.onBackgroundVariant, lineHeight = 26.sp,
                        ),
                    )
                }
            }
        }
    }
}

// ── 삭제 다이얼로그 ─────────────────────────────────────────────────────────

@Composable
private fun DeleteSessionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("기록 삭제", style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, color = SwingTheme.colors.onBackground,
            ))
        },
        text = {
            Text("이 훈련 기록을 영구적으로 삭제하시겠습니까? 삭제된 데이터는 복구할 수 없습니다.",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif, color = SwingTheme.colors.subGray))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("삭제", style = MaterialTheme.typography.labelLarge.copy(color = SwingTheme.colors.danger, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", style = MaterialTheme.typography.labelLarge.copy(color = SwingTheme.colors.onBackground))
            }
        },
        containerColor = SwingTheme.colors.cardSurface,
        shape = RoundedCornerShape(16.dp),
    )
}

// ── NeonProgressBar ─────────────────────────────────────────────────────────

@Composable
fun NeonProgressBar(
    progress: Float,
    brush: Brush,
    glowColor: Color,
    modifier: Modifier = Modifier,
    label: String = "",
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) progress else 0f,
        animationSpec = tween(durationMillis = 1400, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "neon_progress",
    )

    val scanningTrackColor = SwingTheme.colors.scanningTrack
    val pctText = "${(progress * 100).toInt()}%"

    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (label.isNotEmpty()) "$label $pctText" else "Progress $pctText"
        },
    ) {
        val cornerRadius = CornerRadius(size.height / 2f)
        drawRoundRect(color = scanningTrackColor, size = size, cornerRadius = cornerRadius)
        val progressWidth = size.width * animatedProgress
        if (progressWidth > 0f) {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = glowColor
                    asFrameworkPaint().apply { maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL) }
                }
                canvas.drawRoundRect(0f, 0f, progressWidth, size.height, cornerRadius.x, cornerRadius.y, paint)
            }
            drawRoundRect(brush = brush, size = Size(progressWidth, size.height), cornerRadius = cornerRadius)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.5f),
                size = Size(progressWidth, size.height * 0.4f),
                topLeft = Offset(0f, size.height * 0.3f),
                cornerRadius = CornerRadius(size.height * 0.2f),
            )
        }
    }
}
