package com.example.whispertime.ui.timer

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 计时页主内容模式。 */
private enum class TimerViewMode {
    /** 计时主页面。 */
    TIMER,

    /** 项目设置页面。 */
    SETTINGS
}

/** 底部面板标签页。 */
private enum class BottomTab {
    /** 历史记录标签页。 */
    HISTORY,

    /** 统计数据标签页。 */
    STATS
}

/** 底部面板展开状态。 */
private enum class BottomPanelState {
    /** 仅显示拖拽把手。 */
    COLLAPSED,

    /** 半屏展示。 */
    HALF,

    /** 近似全屏展示。 */
    EXPANDED
}

/** 底部面板统一圆角形状。 */
private val PanelShape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)

/** 电池优化提示偏好文件名。 */
private const val BATTERY_EXEMPTION_PREFS = "battery_exemption_prompt"

/** 是否已经请求过忽略电池优化的标记 key。 */
private const val KEY_BATTERY_EXEMPTION_REQUESTED = "requested"

/** 必要时请求用户将应用加入电池优化白名单，以提高长时间后台计时稳定性。 */
private fun requestBatteryOptimizationExemptionIfNeeded(context: Context) {
    val powerManager = context.getSystemService(PowerManager::class.java)
    if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return

    val preferences = context.getSharedPreferences(BATTERY_EXEMPTION_PREFS, Context.MODE_PRIVATE)
    if (preferences.getBoolean(KEY_BATTERY_EXEMPTION_REQUESTED, false)) return

    // 先记录已请求，避免用户拒绝后每次进入计时页都被打扰。
    preferences.edit().putBoolean(KEY_BATTERY_EXEMPTION_REQUESTED, true).apply()

    val packageUri = Uri.parse("package:${context.packageName}")
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = packageUri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = packageUri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val requestStarted = runCatching {
        context.startActivity(requestIntent)
    }.isSuccess
    if (!requestStarted) {
        // 部分 ROM 不支持白名单请求页时，退回到应用详情页让用户手动设置。
        runCatching {
            context.startActivity(fallbackIntent)
        }
    }
}

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

    if (editingRecord != null) {
        AlertDialog(
            onDismissRequest = { editingRecord = null },
            title = { Text("修改记录时长") },
            text = {
                OutlinedTextField(
                    value = editDurationSeconds,
                    onValueChange = { editDurationSeconds = digitsOnly(it) },
                    label = { Text("总秒数") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val record = editingRecord
                        val seconds = editDurationSeconds.toLongOrNull()
                        if (record != null && seconds != null && seconds > 0) {
                            viewModel.updateRecordDurationSeconds(record, seconds)
                        }
                        editingRecord = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRecord = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteProjectDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProjectDialog = false },
            title = { Text("删除项目") },
            text = { Text("确定要删除当前项目吗？项目下的记录也会一并删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteProjectDialog = false
                        viewModel.deleteCurrentProject()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProjectDialog = false }) {
                    Text("取消")
                }
            }
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewMode = TimerViewMode.TIMER }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Text("项目设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                        
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("准备倒计时（秒）", style = MaterialTheme.typography.labelLarge)
                                OutlinedTextField(
                                    value = prepareSecondsText,
                                    onValueChange = { prepareSecondsText = digitsOnly(it) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("语音播报", style = MaterialTheme.typography.labelLarge)
                                    Switch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it })
                                }
                                OutlinedTextField(
                                    value = voiceIntervalSecondsText,
                                    onValueChange = { voiceIntervalSecondsText = digitsOnly(it) },
                                    singleLine = true,
                                    enabled = voiceEnabled,
                                    label = { Text("间隔（秒）") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("震动提醒", style = MaterialTheme.typography.labelLarge)
                                    Switch(
                                        checked = vibrationReminderEnabled,
                                        onCheckedChange = { vibrationReminderEnabled = it }
                                    )
                                }
                                Text(
                                    text = "周期震动复用语音播报间隔；准备倒计时、开始和结束也会震动",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("计时模式", style = MaterialTheme.typography.labelLarge)
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
                            }
                        }

                        Button(
                            onClick = {
                                saveProjectSettings()
                                viewMode = TimerViewMode.TIMER
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("保存设置")
                        }

                        TextButton(
                            onClick = { showDeleteProjectDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text("删除项目")
                        }
                    }
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

/** 计时圆盘组件，绘制进度环、时间文本和暂停态停止按钮。 */
@Composable
private fun TimerCircle(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    progress: Float,
    isPreparing: Boolean,
    preparingText: String,
    timeText: String,
    showStopButton: Boolean,
    primaryHintText: String,
    secondaryHintText: String?,
    onClick: () -> Unit,
    onStop: () -> Unit = {}
) {
    /** 圆盘点击交互源。 */
    val interactionSource = remember { MutableInteractionSource() }

    /** 圆盘是否处于按压状态。 */
    val isPressed by interactionSource.collectIsPressedAsState()

    /** 按压缩放动画值。 */
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "timer_circle_scale"
    )

    /** 按压时外圈光晕透明度。 */
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.28f else 0.12f,
        animationSpec = tween(durationMillis = 140),
        label = "timer_circle_glow"
    )

    Box(
        modifier = modifier
            .size(292.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = true,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 用 Canvas 手绘圆环，避免多个布局叠加导致进度和文本不对齐。
            val radius = size.minDimension / 2f - 10.dp.toPx()
            val stroke = 8.dp.toPx()

            drawCircle(
                color = primaryColor.copy(alpha = glowAlpha),
                radius = radius + 10.dp.toPx(),
                style = Stroke(width = 12.dp.toPx())
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = radius,
                style = Stroke(width = 4.dp.toPx())
            )

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(primaryColor, primaryColor.copy(alpha = 0.4f), primaryColor)
                ),
                startAngle = -90f,
                sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                useCenter = false,
                topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.03f),
                radius = radius - 20.dp.toPx(),
                blendMode = BlendMode.SrcOver
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isPreparing) {
                Text(
                    text = preparingText,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 100.sp),
                    color = primaryColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp
                )
            } else {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 78.sp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp,
                    textAlign = TextAlign.Center
                )

                if (showStopButton) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStop,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    ) {
                        Text(primaryHintText, fontWeight = FontWeight.Bold)
                    }
                    secondaryHintText?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = primaryHintText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/** 项目抽屉，用于在计时页内切换项目或创建新项目。 */
@Composable
private fun ProjectDrawer(
    visible: Boolean,
    projects: List<com.example.whispertime.data.local.entity.ProjectEntity>,
    projectId: Long,
    onDismiss: () -> Unit,
    onNavigateToTimer: (Long) -> Unit,
    onNavigateToCreateProject: () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(onClick = onDismiss)
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = tween(260)) { -it } + fadeIn(tween(220)),
        exit = slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut(tween(160))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxSize(),
            shape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("切换项目", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projects, key = { it.id }) { project ->
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    // 点击当前项目只关闭抽屉，点击其他项目才触发导航。
                                    if (project.id != projectId) {
                                        onNavigateToTimer(project.id)
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (project.id == projectId) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(project.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${if (project.id == projectId) "当前项目" else "点击切换"} · ${project.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onNavigateToCreateProject()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("新建项目")
                }
            }
        }
    }
}

/** 计时页上方的小型数字设置输入框。 */
@Composable
private fun SmallSettingField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .width(90.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 相邻项目提示，用于提示左右滑动切换目标。 */
@Composable
private fun NeighborProjectHint(
    modifier: Modifier = Modifier,
    title: String,
    projectName: String?,
    alignEnd: Boolean
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = projectName ?: "无",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
        )
    }
}

/** 底部历史/统计面板，支持拖拽折叠、半屏和展开。 */
@Composable
private fun BottomPanel(
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
                            val threshold = 100f
                            val newState = when {
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
                            val threshold = 100f
                            val newState = when {
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
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
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

            if (panelState != BottomPanelState.COLLAPSED) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onChangeTab(BottomTab.HISTORY) }, enabled = !disabled) {
                        Text(
                            "HISTORY",
                            color = if (tab == BottomTab.HISTORY) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                    TextButton(onClick = { onChangeTab(BottomTab.STATS) }, enabled = !disabled) {
                        Text(
                            "STATS",
                            color = if (tab == BottomTab.STATS) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                }

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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (records.isEmpty()) {
                        item {
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
                    } else {
                        items(records.take(20), key = { it.id }) { record ->
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
                                } else null
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
                    }
                }
            } else {
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
        }
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
        } else null
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
        } else null
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

/** 过滤输入文本中的非数字字符。 */
private fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

/** 将秒数格式化为计时圆盘使用的 mm:ss 或 hh:mm:ss。 */
private fun formatLarge(totalSecondsInput: Long): String {
    val totalSeconds = totalSecondsInput.coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/** 将毫秒时长格式化为固定 hh:mm:ss。 */
private fun formatDurationHms(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

/** 将时间戳格式化为月/日和时分。 */
private fun formatDate(epochMs: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(epochMs))
}

/** 将时间戳格式化为时分。 */
private fun formatClock(epochMs: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}
