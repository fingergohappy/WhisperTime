package com.example.whispertime.ui.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.timer.TimerConfig
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import com.example.whispertime.ui.timer.screen.BottomPanel
import com.example.whispertime.ui.timer.screen.BottomPanelState
import com.example.whispertime.ui.timer.screen.BottomTab
import com.example.whispertime.ui.timer.screen.DeleteProjectDialog
import com.example.whispertime.ui.timer.screen.EditRecordDurationDialog
import com.example.whispertime.ui.timer.screen.EmptyProjectState
import com.example.whispertime.ui.timer.screen.ProjectDrawer
import com.example.whispertime.ui.timer.screen.TimerCircleStage
import com.example.whispertime.ui.timer.screen.TimerProjectHeader
import com.example.whispertime.ui.timer.screen.TimerSettingsScreen
import com.example.whispertime.ui.timer.screen.TimerViewMode

/** 计时页纯界面内容，供真实页面和 Preview 复用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerScreenContent(
    projectId: Long,
    timerState: TimerState,
    elapsedMs: Long,
    remainingMs: Long?,
    prepareRemainingMs: Long?,
    config: TimerConfig?,
    projectName: String,
    projects: List<ProjectEntity>,
    records: List<TimingRecordEntity>,
    totalDurationMs: Long,
    recordCount: Int,
    averageDurationMs: Long,
    weeklyStats: List<TimerViewModel.WeeklyStat>,
    onNavigateToTimer: (Long) -> Unit,
    onNavigateToCreateProject: () -> Unit,
    onUpdateProjectConfig: (TimerMode, Long?, Long?, Boolean, Long?) -> Unit,
    onCancelTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onStartTimer: (TimerMode?, Long?, Long?, Boolean?, Long?) -> Unit,
    onStopTimer: () -> Unit,
    onDeleteCurrentProject: () -> Unit,
    onDeleteRecord: (TimingRecordEntity) -> Unit,
    onUpdateRecordDurationSeconds: (TimingRecordEntity, Long) -> Unit
) {
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
    val stageLayout = timerStageLayout(timerState)
    val isTimerFocused = !stageLayout.showAmbientChrome

    // 进入运行、准备或暂停时收起底部面板到半屏，让计时圆盘获得焦点。
    LaunchedEffect(isTimerFocused) {
        if (isTimerFocused) {
            bottomPanelState = BottomPanelState.HALF
        }
    }

    val canOpenSettings = !isRunning && !isPreparing
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
        onUpdateProjectConfig(
            selectedMode,
            if (selectedMode == TimerMode.COUNTDOWN) countdownSecondsText.toLongOrNull()?.coerceAtLeast(1L) else null,
            if (voiceEnabled) voiceIntervalSecondsText.toLongOrNull()?.coerceAtLeast(1L) else null,
            vibrationReminderEnabled,
            prepareSecondsText.toLongOrNull()?.coerceAtLeast(0L)
        )
    }

    /** 处理计时圆盘点击，根据当前状态执行开始、暂停、继续或取消准备倒计时。 */
    fun onCircleClick() {
        when {
            isPreparing -> {
                onCancelTimer()
                bottomTab = BottomTab.HISTORY
            }
            isRunning -> onPauseTimer()
            isPaused -> onResumeTimer()
            else -> {
                saveProjectSettings()
                // 启动计时时把当前页面设置作为覆盖项传入，不必等待数据库回流。
                onStartTimer(
                    selectedMode,
                    if (selectedMode == TimerMode.COUNTDOWN) {
                        countdownSecondsText.toLongOrNull()?.coerceAtLeast(1L)?.times(1000L)
                    } else {
                        null
                    },
                    if (voiceEnabled) {
                        voiceIntervalSecondsText.toLongOrNull()?.coerceAtLeast(1L)?.times(1000L)
                    } else {
                        null
                    },
                    vibrationReminderEnabled,
                    prepareSecondsText.toLongOrNull()?.coerceAtLeast(0L)?.times(1000L)
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
                onUpdateRecordDurationSeconds(targetRecord, seconds)
            }
        )
    }

    if (showDeleteProjectDialog) {
        DeleteProjectDialog(
            onDismiss = { showDeleteProjectDialog = false },
            onConfirm = onDeleteCurrentProject
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) {
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
                            EmptyProjectState(
                                modifier = Modifier.fillMaxSize(),
                                onNavigateToCreateProject = onNavigateToCreateProject
                            )
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
                                TimerProjectHeader(
                                    projectName = projectName,
                                    showNeighborHints = projects.size > 1,
                                    previousProjectName = previousProjectName,
                                    nextProjectName = nextProjectName,
                                    canOpenSettings = canOpenSettings,
                                    onOpenDrawer = { showDrawer = true },
                                    onOpenSettings = { viewMode = TimerViewMode.SETTINGS }
                                )
                            }

                            TimerCircleStage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                stageLayout = stageLayout,
                                timerState = timerState,
                                selectedMode = selectedMode,
                                elapsedMs = elapsedMs,
                                remainingMs = remainingMs,
                                prepareRemainingMs = prepareRemainingMs,
                                prepareSecondsText = prepareSecondsText,
                                countdownSecondsText = countdownSecondsText,
                                voiceIntervalSecondsText = voiceIntervalSecondsText,
                                bottomPanelState = bottomPanelState,
                                showDrawer = showDrawer,
                                onBottomPanelStateChange = { bottomPanelState = it },
                                onSelectedModeChange = { selectedMode = it },
                                onPrepareSecondsTextChange = { prepareSecondsText = it },
                                onCountdownSecondsTextChange = { countdownSecondsText = it },
                                onVoiceIntervalSecondsTextChange = { voiceIntervalSecondsText = it },
                                onCircleClick = ::onCircleClick,
                                onStop = {
                                    onStopTimer()
                                    bottomPanelState = BottomPanelState.HALF
                                }
                            )

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
                                onDeleteRecord = onDeleteRecord
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
