package com.example.whispertime.ui.timer.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val targetHeight = when (panelState) {
        BottomPanelState.COLLAPSED -> 64.dp
        BottomPanelState.HALF -> 380.dp
        BottomPanelState.EXPANDED -> 760.dp
    }

    val density = LocalContext.current.resources.displayMetrics.density
    val animatedHeight = remember { Animatable(targetHeight.value) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(targetHeight) {
        animatedHeight.animateTo(
            targetValue = targetHeight.value,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
        )
    }

    val currentHeight = animatedHeight.value.dp.coerceIn(64.dp, 800.dp)

    Surface(
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
                                animatedHeight.snapTo((animatedHeight.value - dragAmount / density).coerceIn(64f, 800f))
                            }
                        },
                        onDragEnd = {
                            val newState = nextPanelState(panelState, dragAccumulator)
                            onSetPanelState(newState)
                            dragAccumulator = 0f
                        }
                    )
                }
            },
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
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

                Spacer(modifier = Modifier.height(20.dp))

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
                        onDeleteRecord = onDeleteRecord
                    )

                    if (disabled) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }
    }
}

private fun nextPanelState(panelState: BottomPanelState, dragAccumulator: Float): BottomPanelState {
    val threshold = 90f
    return when {
        dragAccumulator <= -threshold -> when (panelState) {
            BottomPanelState.COLLAPSED -> BottomPanelState.HALF
            BottomPanelState.HALF -> BottomPanelState.EXPANDED
            else -> BottomPanelState.EXPANDED
        }
        dragAccumulator >= threshold -> when (panelState) {
            BottomPanelState.EXPANDED -> BottomPanelState.HALF
            BottomPanelState.HALF -> BottomPanelState.COLLAPSED
            else -> BottomPanelState.COLLAPSED
        }
        else -> panelState
    }
}

@Composable
private fun PanelHandle(
    panelState: BottomPanelState,
    disabled: Boolean,
    onSetPanelState: (BottomPanelState) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled) {
                if (panelState == BottomPanelState.COLLAPSED) {
                    onSetPanelState(BottomPanelState.HALF)
                } else {
                    onSetPanelState(BottomPanelState.COLLAPSED)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 14.dp)
                .width(44.dp)
                .height(5.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f), CircleShape)
        )
    }
}

@Composable
private fun PanelTabs(
    tab: BottomTab,
    disabled: Boolean,
    onChangeTab: (BottomTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabItem(
            label = "HISTORY",
            selected = tab == BottomTab.HISTORY,
            enabled = !disabled,
            modifier = Modifier.weight(1f),
            onClick = { onChangeTab(BottomTab.HISTORY) }
        )
        TabItem(
            label = "STATS",
            selected = tab == BottomTab.STATS,
            enabled = !disabled,
            modifier = Modifier.weight(1f),
            onClick = { onChangeTab(BottomTab.STATS) }
        )
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(250),
        label = "tab_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "tab_content"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}

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
    onDeleteRecord: (TimingRecordEntity) -> Unit
) {
    var horizontalDragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.pointerInput(tab, enableHorizontalSwipe) {
            if (enableHorizontalSwipe) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDragAccumulator += dragAmount
                    },
                    onDragEnd = {
                        if (horizontalDragAccumulator <= -60f && tab == BottomTab.HISTORY) {
                            onChangeTab(BottomTab.STATS)
                        } else if (horizontalDragAccumulator >= 60f && tab == BottomTab.STATS) {
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
                    (slideInHorizontally { it / 2 } + fadeIn()).togetherWith(
                        slideOutHorizontally { -it / 2 } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { -it / 2 } + fadeIn()).togetherWith(
                        slideOutHorizontally { it / 2 } + fadeOut()
                    )
                }
            },
            label = "tab_content"
        ) { currentTab ->
            if (currentTab == BottomTab.HISTORY) {
                HistoryList(
                    records = records,
                    disabled = disabled,
                    onEditRecord = onEditRecord,
                    onDeleteRecord = onDeleteRecord
                )
            } else {
                StatsPanel(
                    totalDurationMs = totalDurationMs,
                    recordCount = recordCount,
                    averageDurationMs = averageDurationMs,
                    weeklyStats = weeklyStats
                )
            }
        }
    }
}

@Composable
private fun HistoryList(
    records: List<TimingRecordEntity>,
    disabled: Boolean,
    onEditRecord: (TimingRecordEntity) -> Unit,
    onDeleteRecord: (TimingRecordEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (records.isEmpty()) {
            item {
                EmptyHistoryCard()
            }
        } else {
            items(records, key = { it.id }) { record ->
                HistoryRecordItem(
                    record = record,
                    disabled = disabled,
                    onEditRecord = onEditRecord,
                    onDeleteRecord = onDeleteRecord
                )
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "暂无计时记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "点击上方圆盘开始你的第一段专注",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun HistoryRecordItem(
    record: TimingRecordEntity,
    disabled: Boolean,
    onEditRecord: (TimingRecordEntity) -> Unit,
    onDeleteRecord: (TimingRecordEntity) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "press_scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !disabled,
                onClick = { onEditRecord(record) }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDurationHms(record.durationMs),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${formatDate(record.startTime)} · ${formatClock(record.startTime)} - ${formatClock(record.endTime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Light
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { onEditRecord(record) },
                    enabled = !disabled,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
                IconButton(
                    onClick = { onDeleteRecord(record) },
                    enabled = !disabled,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(
    totalDurationMs: Long,
    recordCount: Int,
    averageDurationMs: Long,
    weeklyStats: List<TimerViewModel.WeeklyStat>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "Total Time",
                value = formatLarge(totalDurationMs / 1000L),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Sessions",
                value = recordCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        StatCard(
            label = "Average Session",
            value = formatLarge(averageDurationMs / 1000L),
            modifier = Modifier.fillMaxWidth()
        )
        
        WeeklyChart(weeklyStats)
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun WeeklyChart(weeklyStats: List<TimerViewModel.WeeklyStat>) {
    if (weeklyStats.isEmpty()) return
    val max = weeklyStats.maxOf { it.durationMs }.coerceAtLeast(1L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Weekly Activity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyStats.forEach { stat ->
                    val h = (stat.durationMs.toFloat() / max.toFloat()).coerceIn(0.04f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((h * 100).dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    ),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            stat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
