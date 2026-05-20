package com.example.whispertime.ui.timer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import com.example.whispertime.ui.timer.screen.BottomPanel
import com.example.whispertime.ui.timer.screen.BottomPanelState
import com.example.whispertime.ui.timer.screen.BottomTab
import com.example.whispertime.ui.timer.screen.DeleteProjectDialog
import com.example.whispertime.ui.timer.screen.EditRecordDurationDialog
import com.example.whispertime.ui.timer.screen.NeighborProjectHint
import com.example.whispertime.ui.timer.screen.ProjectDrawer
import com.example.whispertime.ui.timer.screen.SmallSettingField
import com.example.whispertime.ui.timer.screen.TimerCircle
import com.example.whispertime.ui.timer.screen.TimerSettingsScreen
import com.example.whispertime.ui.timer.screen.TimerViewMode
import com.example.whispertime.ui.timer.screen.digitsOnly
import com.example.whispertime.ui.timer.screen.formatLarge
import com.example.whispertime.ui.timer.screen.requestBatteryOptimizationExemptionIfNeeded

/** 计时主页面，承载计时圆盘、项目切换、设置、历史记录和统计面板。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    projectId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToRecords: (Long) -> Unit,
    onNavigateToTimer: (Long) -> Unit,
    onNavigateToCreateProject: () -> Unit,
    viewModel: TimerViewModel = viewModel(
        factory = TimerViewModel.factory(
            LocalContext.current.applicationContext as Application,
            projectId
        )
    )
) {
    val context = LocalContext.current
    // 订阅计时状态和项目数据，驱动页面的所有可视状态。
    val timerState by viewModel.timerState.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val remainingMs by viewModel.remainingMs.collectAsState()
    val prepareRemainingMs by viewModel.prepareRemainingMs.collectAsState()
    val config by viewModel.config.collectAsState()
    val projectName by viewModel.projectName.collectAsState()
    val projects by viewModel.allProjects.collectAsState()
    val records by viewModel.records.collectAsState()
    val totalDurationMs by viewModel.totalDurationMs.collectAsState()
    val recordCount by viewModel.recordCount.collectAsState()
    val averageDurationMs by viewModel.averageDurationMs.collectAsState()
    val weeklyStats by viewModel.weeklyStats.collectAsState()

    /** 通知权限请求 launcher。 */
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // 首次进入计时页时请求通知权限和电池优化豁免。
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestBatteryOptimizationExemptionIfNeeded(context)
    }

    // 页面交互状态。
    var viewMode by remember { mutableStateOf(TimerViewMode.TIMER) }
    var showDrawer by remember { mutableStateOf(false) }
    var bottomPanelState by remember(projectId) { mutableStateOf(BottomPanelState.HALF) }
    var bottomTab by remember { mutableStateOf(BottomTab.HISTORY) }
    var editingRecord by remember { mutableStateOf<TimingRecordEntity?>(null) }
    var editDurationSeconds by remember { mutableStateOf("") }
    var showDeleteProjectDialog by remember { mutableStateOf(false) }

    // 当前项目的可编辑计时配置状态。
    var selectedMode by remember { mutableStateOf(TimerMode.COUNT_UP) }
    var countdownSecondsText by remember { mutableStateOf("180") }
    var prepareSecondsText by remember { mutableStateOf("5") }
    var voiceIntervalSecondsText by remember { mutableStateOf("60") }
    var voiceEnabled by remember { mutableStateOf(true) }
    var vibrationReminderEnabled by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var projectSwipeAccumulator by remember(projectId) { mutableFloatStateOf(0f) }

    // 项目配置变化时刷新设置输入；运行中的计时不覆盖用户正在使用的配置。
    LaunchedEffect(config) {
        val current = config ?: return@LaunchedEffect
        if (!initialized || timerState == TimerState.IDLE) {
            selectedMode = current.mode
            countdownSecondsText = ((current.durationMs ?: 180_000L) / 1000L).toString()
            prepareSecondsText = ((current.prepareTimeMs ?: 5_000L) / 1000L).toString()
            voiceIntervalSecondsText = ((current.voiceIntervalMs ?: 60_000L) / 1000L).toString()
            voiceEnabled = (current.voiceIntervalMs ?: 0L) > 0L
            vibrationReminderEnabled = current.vibrationEnabled
            initialized = true
        }
    }

    // 计时状态派生字段，避免 UI 分支重复判断。
    val isRunning = timerState == TimerState.RUNNING
    val isPreparing = timerState == TimerState.PREPARING
    val isPaused = timerState == TimerState.PAUSED
    val isTimerActive = isRunning || isPreparing
    val idleAtZero = timerState == TimerState.IDLE && elapsedMs == 0L
    val isTimerFocused = isTimerActive || isPaused
    val stageLayout = timerStageLayout(timerState)
    val circleUiState = timerCircleUiState(timerState)

    // 进入运行、准备或暂停时收起底部面板到半屏，让计时圆盘获得焦点。
    LaunchedEffect(isTimerFocused) {
        if (isTimerFocused) {
            bottomPanelState = BottomPanelState.HALF
        }
    }

    // 项目被删除后返回上一页。
    LaunchedEffect(Unit) {
        viewModel.deleteResult.collect { deleted ->
            if (deleted) {
                onNavigateBack()
            }
        }
    }

    val canOpenSettings = !isRunning && !isPreparing

    // 圆盘中央展示的秒数，准备态展示准备剩余，倒计时展示剩余，正计时展示已过。
    val displaySeconds = when {
        isPreparing -> ((prepareRemainingMs ?: 0L) + 999L) / 1000L
        selectedMode == TimerMode.COUNTDOWN -> ((remainingMs ?: 0L) / 1000L).coerceAtLeast(0L)
        else -> (elapsedMs / 1000L).coerceAtLeast(0L)
    }

    // 环形进度按不同模式映射到 0..1。
    val ringProgressTarget = when {
        isPreparing -> {
            val total = prepareSecondsText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            ((total - displaySeconds).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
        selectedMode == TimerMode.COUNTDOWN -> {
            val total = countdownSecondsText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            (1f - (displaySeconds.toFloat() / total.toFloat())).coerceIn(0f, 1f)
        }
        else -> ((elapsedMs / 1000L) % 60L).toFloat() / 60f
    }

    // 倒计时模式下：接近结束时（剩余<=3秒）跳过动画，确保与语音同步
    // 正计时模式下：每分钟整点时跳过动画
    val shouldSkipRingAnimation = when {
        selectedMode == TimerMode.COUNTDOWN && !isPreparing -> {
            val remaining = remainingMs ?: 0L
            remaining <= 3000L || remaining == 0L
        }
        selectedMode == TimerMode.COUNT_UP && !isPreparing -> {
            elapsedMs > 0L && elapsedMs % 60_000L == 0L
        }
        else -> false
    }
    // 倒计时使用更快的动画（300ms）以保持与语音同步
    val ringAnimationDuration = if (selectedMode == TimerMode.COUNTDOWN && !isPreparing) 300 else 900
    val animatedRingProgress by animateFloatAsState(
        targetValue = ringProgressTarget,
        animationSpec = tween(durationMillis = ringAnimationDuration),
        label = "ring_progress"
    )
    /** 实际绘制使用的环形进度。 */
    val ringProgress = if (shouldSkipRingAnimation) ringProgressTarget else animatedRingProgress
    /** 计时聚焦时的圆盘缩放动画值。 */
    val timerCircleFocusScale by animateFloatAsState(
        targetValue = if (isTimerFocused) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f),
        label = "timer_circle_focus_scale"
    )
    /** 圆盘纵向偏移动画值。 */
    val timerCircleOffsetY by animateDpAsState(
        targetValue = stageLayout.circleOffsetYDp.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f),
        label = "timer_circle_focus_offset"
    )
    /** 当前项目在项目列表中的索引。 */
    val currentProjectIndex = remember(projectId, projects) {
        projects.indexOfFirst { it.id == projectId }
    }
    /** 左侧相邻项目名称。 */
    val previousProjectName = remember(currentProjectIndex, projects) {
        if (currentProjectIndex > 0) projects[currentProjectIndex - 1].name else null
    }
    /** 右侧相邻项目名称。 */
    val nextProjectName = remember(currentProjectIndex, projects) {
        if (currentProjectIndex in 0 until projects.lastIndex) projects[currentProjectIndex + 1].name else null
    }

    /** 根据偏移量切换到相邻项目。 */
    fun navigateToAdjacentProject(offset: Int) {
        if (projects.size <= 1 || currentProjectIndex == -1) return
        val targetIndex = (currentProjectIndex + offset).coerceIn(0, projects.lastIndex)
        if (targetIndex != currentProjectIndex) {
            onNavigateToTimer(projects[targetIndex].id)
        }
    }

    /** 保存页面当前设置到项目默认配置。 */
    fun saveProjectSettings() {
        viewModel.updateProjectConfig(
            mode = selectedMode,
            countdownSeconds = if (selectedMode == TimerMode.COUNTDOWN) countdownSecondsText.toLongOrNull()?.coerceAtLeast(1L) else null,
            voiceIntervalSeconds = if (voiceEnabled) voiceIntervalSecondsText.toLongOrNull()?.coerceAtLeast(1L) else null,
            vibrationEnabled = vibrationReminderEnabled,
            prepareSeconds = prepareSecondsText.toLongOrNull()?.coerceAtLeast(0L)
        )
    }

    /** 处理计时圆盘点击，根据当前状态执行开始、暂停、继续或取消准备倒计时。 */
    fun onCircleClick() {
        when {
            isPreparing -> {
                viewModel.cancelTimer()
                bottomTab = BottomTab.HISTORY
            }
            isRunning -> viewModel.pauseTimer()
            isPaused -> viewModel.resumeTimer()
            else -> {
                saveProjectSettings()
                // 启动计时时把当前页面设置作为覆盖项传入，不必等待数据库回流。
                viewModel.startTimer(
                    modeOverride = selectedMode,
                    durationOverride = if (selectedMode == TimerMode.COUNTDOWN) {
                        countdownSecondsText.toLongOrNull()?.coerceAtLeast(1L)?.times(1000L)
                    } else {
                        null
                    },
                    intervalOverride = if (voiceEnabled) {
                        voiceIntervalSecondsText.toLongOrNull()?.coerceAtLeast(1L)?.times(1000L)
                    } else {
                        null
                    },
                    vibrationOverride = vibrationReminderEnabled,
                    prepareOverrideMs = prepareSecondsText.toLongOrNull()?.coerceAtLeast(0L)?.times(1000L)
                )
            }
        }
    }

    editingRecord?.let { record ->
        EditRecordDurationDialog(
            record = record,
            editDurationSeconds = editDurationSeconds,
            onEditDurationSecondsChange = { editDurationSeconds = it },
            onDismiss = { editingRecord = null },
            onConfirm = { targetRecord, seconds ->
                viewModel.updateRecordDurationSeconds(targetRecord, seconds)
            }
        )
    }

    if (showDeleteProjectDialog) {
        DeleteProjectDialog(
            onDismiss = { showDeleteProjectDialog = false },
            onConfirm = { viewModel.deleteCurrentProject() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            AnimatedContent(
                targetState = viewMode,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(260)) +
                        slideInHorizontally(animationSpec = tween(260)) { it / 10 }) togetherWith
                        (fadeOut(animationSpec = tween(200)) +
                            slideOutHorizontally(animationSpec = tween(200)) { -it / 14 })
                },
                label = "timer_view_mode"
            ) { currentViewMode ->
                if (currentViewMode == TimerViewMode.SETTINGS) {
                    TimerSettingsScreen(
                        selectedMode = selectedMode,
                        prepareSecondsText = prepareSecondsText,
                        voiceIntervalSecondsText = voiceIntervalSecondsText,
                        voiceEnabled = voiceEnabled,
                        vibrationReminderEnabled = vibrationReminderEnabled,
                        onSelectedModeChange = { selectedMode = it },
                        onPrepareSecondsTextChange = { prepareSecondsText = it },
                        onVoiceIntervalSecondsTextChange = { voiceIntervalSecondsText = it },
                        onVoiceEnabledChange = { voiceEnabled = it },
                        onVibrationReminderEnabledChange = { vibrationReminderEnabled = it },
                        onBack = { viewMode = TimerViewMode.TIMER },
                        onSave = {
                            saveProjectSettings()
                            viewMode = TimerViewMode.TIMER
                        },
                        onDeleteProject = { showDeleteProjectDialog = true }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.background
                                    ),
                                    radius = 1200f
                                )
                            )
                            .pointerInput(projectId, projects, viewMode, showDrawer, isTimerFocused) {
                                if (
                                    viewMode == TimerViewMode.TIMER &&
                                    !isTimerFocused &&
                                    !showDrawer &&
                                    projects.size > 1
                                ) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, dragAmount ->
                                            projectSwipeAccumulator += dragAmount
                                        },
                                        onDragEnd = {
                                            // 空闲态水平滑动用于在项目之间快速切换。
                                            when {
                                                projectSwipeAccumulator <= -60f -> navigateToAdjacentProject(1)
                                                projectSwipeAccumulator >= 60f -> navigateToAdjacentProject(-1)
                                            }
                                            projectSwipeAccumulator = 0f
                                        },
                                        onDragCancel = {
                                            projectSwipeAccumulator = 0f
                                        }
                                    )
                                }
                            }
                    ) {
                        if (projects.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("尚未创建项目", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "请先新建项目后开始计时",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(onClick = onNavigateToCreateProject) {
                                            Text("新建项目")
                                        }
                                    }
                                }
                            }
                            return@Box
                        }

                        Column(
                            modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedVisibility(
                                visible = stageLayout.showAmbientChrome,
                                enter = fadeIn(animationSpec = tween(300)) +
                                    slideInVertically(animationSpec = tween(300)) { -it },
                                exit = fadeOut(animationSpec = tween(250)) +
                                    slideOutVertically(animationSpec = tween(250)) { -it }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CenterAlignedTopAppBar(
                                        title = {
                                            AnimatedContent(
                                                targetState = projectName.ifBlank { "无项目" },
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(180)).togetherWith(
                                                        fadeOut(animationSpec = tween(120))
                                                    )
                                                },
                                                label = "project_title"
                                            ) {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        },
                                        navigationIcon = {
                                            IconButton(onClick = { showDrawer = true }) {
                                                Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                                            }
                                        },
                                        actions = {
                                            IconButton(
                                                onClick = { if (canOpenSettings) viewMode = TimerViewMode.SETTINGS },
                                                enabled = canOpenSettings
                                            ) {
                                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                                    )

                                    if (projects.size > 1) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            NeighborProjectHint(
                                                modifier = Modifier.weight(1f),
                                                title = "上一个",
                                                projectName = previousProjectName,
                                                alignEnd = false
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            NeighborProjectHint(
                                                modifier = Modifier.weight(1f),
                                                title = "下一个",
                                                projectName = nextProjectName,
                                                alignEnd = true
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = when (stageLayout.circleAlignment) {
                                    TimerCircleAlignment.TOP_CENTER -> Alignment.TopCenter
                                    TimerCircleAlignment.CENTER -> Alignment.Center
                                }
                            ) {
                                if (!showDrawer && bottomPanelState != BottomPanelState.COLLAPSED) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .pointerInput(showDrawer, bottomPanelState) {
                                                detectTapGestures {
                                                    // 点击圆盘外侧空白区域收起底部面板。
                                                    bottomPanelState = BottomPanelState.COLLAPSED
                                                }
                                            }
                                    )
                                }

                                Column(
                                    modifier = Modifier.padding(top = stageLayout.circleTopPaddingDp.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    AnimatedVisibility(
                                        visible = idleAtZero && !isPreparing,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = selectedMode == TimerMode.COUNT_UP,
                                                    onClick = { selectedMode = TimerMode.COUNT_UP },
                                                    label = { Text("正计时") }
                                                )
                                                FilterChip(
                                                    selected = selectedMode == TimerMode.COUNTDOWN,
                                                    onClick = { selectedMode = TimerMode.COUNTDOWN },
                                                    label = { Text("倒计时") }
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }

                                    TimerCircle(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = timerCircleFocusScale
                                                scaleY = timerCircleFocusScale
                                            }
                                            .offset(y = timerCircleOffsetY),
                                        primaryColor = MaterialTheme.colorScheme.primary,
                                        progress = ringProgress,
                                        isPreparing = isPreparing,
                                        preparingText = if (displaySeconds > 0L) displaySeconds.toString() else "GO!",
                                        timeText = formatLarge(displaySeconds),
                                        showStopButton = circleUiState.showStopButton,
                                        primaryHintText = circleUiState.primaryHintText,
                                        secondaryHintText = circleUiState.secondaryHintText,
                                        onClick = ::onCircleClick,
                                        onStop = { 
                                            viewModel.stopTimer()
                                            bottomPanelState = BottomPanelState.HALF
                                        }
                                    )

                                    AnimatedVisibility(
                                        visible = idleAtZero && !isPreparing,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                SmallSettingField(
                                                    title = "准备(秒)",
                                                    value = prepareSecondsText,
                                                    onValueChange = { prepareSecondsText = digitsOnly(it) }
                                                )
                                                if (selectedMode == TimerMode.COUNTDOWN) {
                                                    SmallSettingField(
                                                        title = "时长(秒)",
                                                        value = countdownSecondsText,
                                                        onValueChange = { countdownSecondsText = digitsOnly(it) }
                                                    )
                                                }
                                                SmallSettingField(
                                                    title = "间隔(秒)",
                                                    value = voiceIntervalSecondsText,
                                                    onValueChange = { voiceIntervalSecondsText = digitsOnly(it) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = stageLayout.showAmbientChrome,
                                enter = fadeIn(animationSpec = tween(300)) +
                                    slideInVertically(animationSpec = tween(300)) { it },
                                exit = fadeOut(animationSpec = tween(250)) +
                                    slideOutVertically(animationSpec = tween(250)) { it }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(modifier = Modifier.height(72.dp))
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = stageLayout.showAmbientChrome,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding(),
                            enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(animationSpec = tween(300)) { it },
                            exit = fadeOut(animationSpec = tween(250)) +
                                slideOutVertically(animationSpec = tween(250)) { it }
                        ) {
                            BottomPanel(
                                modifier = Modifier,
                                panelState = bottomPanelState,
                                disabled = isPreparing,
                                tab = bottomTab,
                                records = records,
                                totalDurationMs = totalDurationMs,
                                recordCount = recordCount,
                                averageDurationMs = averageDurationMs,
                                weeklyStats = weeklyStats,
                                onSetPanelState = { bottomPanelState = it },
                                onChangeTab = { bottomTab = it },
                                onEditRecord = {
                                    editingRecord = it
                                    editDurationSeconds = (it.durationMs / 1000L).toString()
                                },
                                onDeleteRecord = { viewModel.deleteRecord(it) }
                            )
                        }
                    }
                }
            }
        }

        ProjectDrawer(
            visible = showDrawer,
            projects = projects,
            projectId = projectId,
            onDismiss = { showDrawer = false },
            onNavigateToTimer = onNavigateToTimer,
            onNavigateToCreateProject = onNavigateToCreateProject
        )

    }
}
