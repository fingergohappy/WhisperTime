package com.example.whispertime.ui.timer.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.ui.timer.TimerViewModel
import kotlinx.coroutines.launch

/** 底部历史/统计面板，支持拖拽折叠、半屏和展开。 */
@Composable
internal fun BottomPanel(
    modifier: Modifier = Modifier,
    panelState: BottomPanelState,
    disabled: Boolean,
    tab: BottomTab,
    records: List<TimingRecordEntity>,
    totalDurationMs: Long,
    recordCount: Int,
    averageDurationMs: Long,
    weeklyStats: List<TimerViewModel.WeeklyStat>,
    onSetPanelState: (BottomPanelState) -> Unit,
    onChangeTab: (BottomTab) -> Unit,
    onEditRecord: (TimingRecordEntity) -> Unit,
    onDeleteRecord: (TimingRecordEntity) -> Unit
) {
    /** 当前面板状态对应的目标高度。 */
    val targetHeight = when (panelState) {
        BottomPanelState.COLLAPSED -> 56.dp
        BottomPanelState.HALF -> 360.dp
        BottomPanelState.EXPANDED -> 740.dp
    }

    /** 屏幕密度，用于把拖拽像素转换为 dp。 */
    val density = LocalContext.current.resources.displayMetrics.density

    /** 面板高度动画值。 */
    val animatedHeight = remember { Animatable(targetHeight.value) }

    /** 拖拽过程中 snapTo/animateTo 使用的协程作用域。 */
    val scope = rememberCoroutineScope()

    // 外部状态变化时，平滑动画到对应面板高度。
    LaunchedEffect(targetHeight) {
        animatedHeight.animateTo(
            targetValue = targetHeight.value,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 320f
            )
        )
    }

    /** 约束后的当前面板高度。 */
    val currentHeight = animatedHeight.value.dp.coerceIn(56.dp, 760.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(currentHeight)
            .pointerInput(disabled, panelState) {
                if (!disabled) {
                    var dragAccumulator = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                            scope.launch {
                                // 手指向上拖时 dragAmount 为负，高度应增加。
                                animatedHeight.snapTo((animatedHeight.value - dragAmount / density).coerceIn(56f, 760f))
                            }
                        },
                        onDragEnd = {
                            // 超过阈值才切换状态，短拖拽回弹到原状态。
                            val newState = nextPanelState(panelState, dragAccumulator)
                            if (newState != panelState) {
                                onSetPanelState(newState)
                            } else {
                                scope.launch {
                                    animatedHeight.animateTo(
                                        targetValue = targetHeight.value,
                                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f)
                                    )
                                }
                            }
                            dragAccumulator = 0f
                        }
                    )
                }
            }
            .pointerInput(disabled, panelState) {
                if (!disabled) {
                    var dragAccumulator = 0f
                    detectDragGesturesAfterLongPress(
                        onDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount.y
                            scope.launch {
                                // 长按后拖拽也使用同一套高度换算逻辑。
                                animatedHeight.snapTo((animatedHeight.value - dragAmount.y / density).coerceIn(56f, 760f))
                            }
                        },
                        onDragEnd = {
                            // 长按拖拽结束后根据累计位移切换面板状态。
                            val newState = nextPanelState(panelState, dragAccumulator)
                            if (newState != panelState) {
                                onSetPanelState(newState)
                            } else {
                                scope.launch {
                                    animatedHeight.animateTo(
                                        targetValue = targetHeight.value,
                                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f)
                                    )
                                }
                            }
                            dragAccumulator = 0f
                        },
                        onDragCancel = {
                            scope.launch {
                                animatedHeight.animateTo(
                                    targetValue = targetHeight.value,
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f)
                                )
                            }
                        }
                    )
                }
            },
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .background(Color.Transparent)
        ) {
            PanelHandle(
                panelState = panelState,
                disabled = disabled,
                onSetPanelState = onSetPanelState
            )

            if (panelState != BottomPanelState.COLLAPSED) {
                PanelTabs(
                    tab = tab,
                    disabled = disabled,
                    onChangeTab = onChangeTab
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    BottomTabContent(
                        modifier = Modifier.fillMaxSize(),
                        tab = tab,
                        records = records,
                        totalDurationMs = totalDurationMs,
                        recordCount = recordCount,
                        averageDurationMs = averageDurationMs,
                        weeklyStats = weeklyStats,
                        disabled = disabled,
                        enableHorizontalSwipe = !disabled,
                        onChangeTab = onChangeTab,
                        onEditRecord = onEditRecord,
                        onDeleteRecord = onDeleteRecord,
                        isFullscreen = panelState == BottomPanelState.EXPANDED
                    )

                    if (disabled) {
                        // 准备倒计时时禁用历史面板交互，保留可见但不可操作的视觉反馈。
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }
    }
}

/** 根据拖拽累计位移计算下一个面板状态。 */
private fun nextPanelState(panelState: BottomPanelState, dragAccumulator: Float): BottomPanelState {
    val threshold = 100f
    return when {
        dragAccumulator <= -threshold -> when (panelState) {
            BottomPanelState.COLLAPSED -> BottomPanelState.HALF
            BottomPanelState.HALF -> BottomPanelState.EXPANDED
            BottomPanelState.EXPANDED -> BottomPanelState.EXPANDED
        }
        dragAccumulator >= threshold -> when (panelState) {
            BottomPanelState.EXPANDED -> BottomPanelState.HALF
            BottomPanelState.HALF -> BottomPanelState.COLLAPSED
            BottomPanelState.COLLAPSED -> BottomPanelState.COLLAPSED
        }
        else -> panelState
    }
}

/** 底部面板顶部拖拽把手。 */
@Composable
private fun PanelHandle(
    panelState: BottomPanelState,
    disabled: Boolean,
    onSetPanelState: (BottomPanelState) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 8.dp)
                .width(46.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), CircleShape)
                .clickable(enabled = !disabled) {
                    if (panelState == BottomPanelState.COLLAPSED) {
                        onSetPanelState(BottomPanelState.HALF)
                    } else {
                        onSetPanelState(BottomPanelState.COLLAPSED)
                    }
                }
        )
    }
}

/** 底部面板历史和统计标签。 */
@Composable
private fun PanelTabs(
    tab: BottomTab,
    disabled: Boolean,
    onChangeTab: (BottomTab) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChangeTab(BottomTab.HISTORY) }, enabled = !disabled) {
            Text(
                "HISTORY",
                color = if (tab == BottomTab.HISTORY) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                letterSpacing = 1.sp
            )
        }
        TextButton(onClick = { onChangeTab(BottomTab.STATS) }, enabled = !disabled) {
            Text(
                "STATS",
                color = if (tab == BottomTab.STATS) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                letterSpacing = 1.sp
            )
        }
    }
}

/** 底部面板内容，负责历史和统计两个标签页的横滑切换。 */
@Composable
private fun BottomTabContent(
    modifier: Modifier,
    tab: BottomTab,
    records: List<TimingRecordEntity>,
    totalDurationMs: Long,
    recordCount: Int,
    averageDurationMs: Long,
    weeklyStats: List<TimerViewModel.WeeklyStat>,
    disabled: Boolean,
    enableHorizontalSwipe: Boolean,
    onChangeTab: (BottomTab) -> Unit,
    onEditRecord: (TimingRecordEntity) -> Unit,
    onDeleteRecord: (TimingRecordEntity) -> Unit,
    isFullscreen: Boolean = false
) {
    /** 横向拖拽累计值，用于判断标签页切换。 */
    var horizontalDragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.pointerInput(tab, enableHorizontalSwipe) {
            if (enableHorizontalSwipe) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        // 左滑进入统计，右滑回到历史。
                        if (horizontalDragAccumulator <= -40f && tab == BottomTab.HISTORY) {
                            onChangeTab(BottomTab.STATS)
                        } else if (horizontalDragAccumulator >= 40f && tab == BottomTab.STATS) {
                            onChangeTab(BottomTab.HISTORY)
                        }
                        horizontalDragAccumulator = 0f
                    }
                )
            }
        }
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it / 3 } + fadeIn()).togetherWith(
                        slideOutHorizontally { -it / 3 } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(
                        slideOutHorizontally { it / 3 } + fadeOut()
                    )
                }
            },
            label = "bottom_tab_switch"
        ) { currentTab ->
            if (currentTab == BottomTab.HISTORY) {
                HistoryList(
                    records = records,
                    disabled = disabled,
                    isFullscreen = isFullscreen,
                    onEditRecord = onEditRecord,
                    onDeleteRecord = onDeleteRecord
                )
            } else {
                StatsPanel(
                    totalDurationMs = totalDurationMs,
                    recordCount = recordCount,
                    averageDurationMs = averageDurationMs,
                    weeklyStats = weeklyStats,
                    isFullscreen = isFullscreen
                )
            }
        }
    }
}

/** 历史记录列表内容。 */
@Composable
private fun HistoryList(
    records: List<TimingRecordEntity>,
    disabled: Boolean,
    isFullscreen: Boolean,
    onEditRecord: (TimingRecordEntity) -> Unit,
    onDeleteRecord: (TimingRecordEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (records.isEmpty()) {
            item {
                EmptyHistoryCard(isFullscreen = isFullscreen)
            }
        } else {
            items(records.take(20), key = { it.id }) { record ->
                HistoryRecordItem(
                    record = record,
                    disabled = disabled,
                    isFullscreen = isFullscreen,
                    onEditRecord = onEditRecord,
                    onDeleteRecord = onDeleteRecord
                )
            }
        }
    }
}

/** 空历史记录提示卡片。 */
@Composable
private fun EmptyHistoryCard(isFullscreen: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFullscreen) 48.dp else 28.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无记录", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** 单条历史记录行。 */
@Composable
private fun HistoryRecordItem(
    record: TimingRecordEntity,
    disabled: Boolean,
    isFullscreen: Boolean,
    onEditRecord: (TimingRecordEntity) -> Unit,
    onDeleteRecord: (TimingRecordEntity) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Card(
        shape = RoundedCornerShape(if (isFullscreen) 22.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFullscreen) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isFullscreen) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = if (isPressed) 0.985f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !disabled,
                    onClick = { onEditRecord(record) }
                )
                .padding(
                    horizontal = if (isFullscreen) 16.dp else 12.dp,
                    vertical = if (isFullscreen) 14.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatDurationHms(record.durationMs),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${formatDate(record.startTime)} - ${formatClock(record.endTime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = { onEditRecord(record) }, enabled = !disabled) { Text("编辑") }
            IconButton(onClick = { onDeleteRecord(record) }, enabled = !disabled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 统计标签内容。 */
@Composable
private fun StatsPanel(
    totalDurationMs: Long,
    recordCount: Int,
    averageDurationMs: Long,
    weeklyStats: List<TimerViewModel.WeeklyStat>,
    isFullscreen: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatItem("Total", formatLarge(totalDurationMs / 1000L), Modifier.weight(1f), true)
                StatItem("Sessions", recordCount.toString(), Modifier.weight(1f), true)
            }
            StatItem("Average Duration", formatLarge(averageDurationMs / 1000L), Modifier.fillMaxWidth(), true)
        } else {
            StatItem("Total Time", formatLarge(totalDurationMs / 1000L))
            StatItem("Sessions", recordCount.toString())
            StatItem("Average", formatLarge(averageDurationMs / 1000L))
        }
        WeeklyChart(weeklyStats, isFullscreen)
    }
}

/** 统计项卡片。 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(if (emphasize) 22.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasize) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (emphasize) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (emphasize) 16.dp else 12.dp,
                    vertical = if (emphasize) 14.dp else 10.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 最近七天柱状图。 */
@Composable
private fun WeeklyChart(weeklyStats: List<TimerViewModel.WeeklyStat>, emphasize: Boolean = false) {
    if (weeklyStats.isEmpty()) return
    /** 柱状图归一化使用的最大时长。 */
    val max = weeklyStats.maxOf { it.durationMs }.coerceAtLeast(1L)

    Card(
        shape = RoundedCornerShape(if (emphasize) 24.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasize) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (emphasize) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.padding(if (emphasize) 14.dp else 10.dp)) {
            if (emphasize) {
                Text(
                    "Weekly Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (emphasize) 120.dp else 90.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weeklyStats.forEach { stat ->
                    // 将每天时长映射为 0..1 的高度比例，并保留最小高度。
                    val h = (stat.durationMs.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((h * if (emphasize) 92f else 70f).dp.coerceAtLeast(4.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                    ),
                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
