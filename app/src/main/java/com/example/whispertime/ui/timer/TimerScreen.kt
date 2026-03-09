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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
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

private enum class TimerViewMode {
    TIMER,
    SETTINGS
}

private enum class BottomTab {
    HISTORY,
    STATS
}

private enum class BottomPanelState {
    COLLAPSED,
    HALF,
    EXPANDED
}

private val PanelShape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)

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

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var viewMode by remember { mutableStateOf(TimerViewMode.TIMER) }
    var showDrawer by remember { mutableStateOf(false) }
    var bottomPanelState by remember(projectId) { mutableStateOf(BottomPanelState.COLLAPSED) }
    var bottomTab by remember { mutableStateOf(BottomTab.HISTORY) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var editingRecord by remember { mutableStateOf<TimingRecordEntity?>(null) }
    var editDurationSeconds by remember { mutableStateOf("") }

    var selectedMode by remember { mutableStateOf(TimerMode.COUNT_UP) }
    var countdownSecondsText by remember { mutableStateOf("180") }
    var prepareSecondsText by remember { mutableStateOf("5") }
    var voiceIntervalSecondsText by remember { mutableStateOf("60") }
    var voiceEnabled by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }
    var projectSwipeAccumulator by remember(projectId) { mutableFloatStateOf(0f) }

    LaunchedEffect(config) {
        val current = config ?: return@LaunchedEffect
        if (!initialized || timerState == TimerState.IDLE) {
            selectedMode = current.mode
            countdownSecondsText = ((current.durationMs ?: 180_000L) / 1000L).toString()
            prepareSecondsText = ((current.prepareTimeMs ?: 5_000L) / 1000L).toString()
            voiceIntervalSecondsText = ((current.voiceIntervalMs ?: 60_000L) / 1000L).toString()
            voiceEnabled = (current.voiceIntervalMs ?: 0L) > 0L
            initialized = true
        }
    }

    val isRunning = timerState == TimerState.RUNNING
    val isPreparing = timerState == TimerState.PREPARING
    val isPaused = timerState == TimerState.PAUSED
    val isTimerActive = isRunning || isPreparing
    val idleAtZero = timerState == TimerState.IDLE && elapsedMs == 0L

    LaunchedEffect(isTimerActive) {
        if (isTimerActive) {
            bottomPanelState = BottomPanelState.COLLAPSED
        }
    }

    val canOpenSettings = !isRunning && !isPreparing

    val displaySeconds = when {
        isPreparing -> ((prepareRemainingMs ?: 0L) + 999L) / 1000L
        selectedMode == TimerMode.COUNTDOWN -> ((remainingMs ?: 0L) / 1000L).coerceAtLeast(0L)
        else -> (elapsedMs / 1000L).coerceAtLeast(0L)
    }

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

    val shouldSkipRingAnimation = selectedMode == TimerMode.COUNT_UP &&
        !isPreparing &&
        elapsedMs > 0L &&
        elapsedMs % 60_000L == 0L
    val animatedRingProgress by animateFloatAsState(
        targetValue = ringProgressTarget,
        animationSpec = tween(durationMillis = 900),
        label = "ring_progress"
    )
    val ringProgress = if (shouldSkipRingAnimation) ringProgressTarget else animatedRingProgress
    val timerCircleFocusScale by animateFloatAsState(
        targetValue = if (isTimerActive) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f),
        label = "timer_circle_focus_scale"
    )
    val timerCircleOffsetY by animateDpAsState(
        targetValue = if (isTimerActive) (-20).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f),
        label = "timer_circle_focus_offset"
    )
    val currentProjectIndex = remember(projectId, projects) {
        projects.indexOfFirst { it.id == projectId }
    }
    val previousProjectName = remember(currentProjectIndex, projects) {
        if (currentProjectIndex > 0) projects[currentProjectIndex - 1].name else null
    }
    val nextProjectName = remember(currentProjectIndex, projects) {
        if (currentProjectIndex in 0 until projects.lastIndex) projects[currentProjectIndex + 1].name else null
    }

    fun navigateToAdjacentProject(offset: Int) {
        if (projects.size <= 1 || currentProjectIndex == -1) return
        val targetIndex = (currentProjectIndex + offset).coerceIn(0, projects.lastIndex)
        if (targetIndex != currentProjectIndex) {
            onNavigateToTimer(projects[targetIndex].id)
        }
    }

    fun saveProjectSettings() {
        viewModel.updateProjectConfig(
            mode = selectedMode,
            countdownSeconds = if (selectedMode == TimerMode.COUNTDOWN) countdownSecondsText.toLongOrNull()?.coerceAtLeast(1L) else null,
            voiceIntervalSeconds = if (voiceEnabled) voiceIntervalSecondsText.toLongOrNull()?.coerceAtLeast(1L) else null,
            prepareSeconds = prepareSecondsText.toLongOrNull()?.coerceAtLeast(0L)
        )
    }

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
                            .pointerInput(projectId, projects, viewMode, showDrawer, isTimerActive) {
                                if (
                                    viewMode == TimerViewMode.TIMER &&
                                    !isTimerActive &&
                                    !showDrawer &&
                                    projects.size > 1
                                ) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, dragAmount ->
                                            projectSwipeAccumulator += dragAmount
                                        },
                                        onDragEnd = {
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
                                visible = !isTimerActive,
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
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 36.dp),
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
                                        isPaused = isPaused,
                                        preparingText = if (displaySeconds > 0L) displaySeconds.toString() else "GO!",
                                        timeText = formatLarge(displaySeconds),
                                        hintText = when {
                                            isPreparing -> "Tap to stop"
                                            isRunning -> "Tap to pause"
                                            isPaused -> "Tap to resume"
                                            else -> "Tap to start"
                                        },
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
                                visible = !isTimerActive,
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
                            visible = !isTimerActive,
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
                                onToggleExpand = {
                                    bottomPanelState = when (bottomPanelState) {
                                        BottomPanelState.COLLAPSED -> BottomPanelState.HALF
                                        BottomPanelState.HALF -> BottomPanelState.EXPANDED
                                        BottomPanelState.EXPANDED -> BottomPanelState.HALF
                                    }
                                },
                                onDragAmount = { dragY ->
                                    dragAccumulator += dragY
                                    dragOffset -= dragY
                                },
                                onDragEnd = {
                                    val threshold = 100f
                                    bottomPanelState = when {
                                        dragAccumulator <= -threshold -> when (bottomPanelState) {
                                            BottomPanelState.COLLAPSED -> BottomPanelState.HALF
                                            BottomPanelState.HALF -> BottomPanelState.EXPANDED
                                            BottomPanelState.EXPANDED -> BottomPanelState.EXPANDED
                                        }
                                        dragAccumulator >= threshold -> when (bottomPanelState) {
                                            BottomPanelState.EXPANDED -> BottomPanelState.HALF
                                            BottomPanelState.HALF -> BottomPanelState.COLLAPSED
                                            BottomPanelState.COLLAPSED -> BottomPanelState.COLLAPSED
                                        }
                                        else -> bottomPanelState
                                    }
                                    dragAccumulator = 0f
                                    dragOffset = 0f
                                },
                                dragOffset = dragOffset,
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

@Composable
private fun TimerCircle(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    progress: Float,
    isPreparing: Boolean,
    isPaused: Boolean = false,
    preparingText: String,
    timeText: String,
    hintText: String,
    onClick: () -> Unit,
    onStop: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "timer_circle_scale"
    )
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
                
                if (isPaused) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStop,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    ) {
                        Text("STOP", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap circle to resume",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

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
    onToggleExpand: () -> Unit,
    onDragAmount: (Float) -> Unit,
    onDragEnd: () -> Unit,
    dragOffset: Float = 0f,
    onChangeTab: (BottomTab) -> Unit,
    onEditRecord: (TimingRecordEntity) -> Unit,
    onDeleteRecord: (TimingRecordEntity) -> Unit
) {
    val targetBaseHeight = when (panelState) {
        BottomPanelState.COLLAPSED -> 82.dp
        BottomPanelState.HALF -> 360.dp
        BottomPanelState.EXPANDED -> 740.dp
    }
    
    val animatedHeight by animateDpAsState(
        targetValue = targetBaseHeight,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 320f
        ),
        label = "bottom_panel_height"
    )

    val currentHeight = (animatedHeight + (dragOffset / LocalContext.current.resources.displayMetrics.density).dp)
        .coerceIn(82.dp, 760.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(currentHeight)
            .pointerInput(disabled) {
                if (!disabled) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount -> onDragAmount(dragAmount) },
                        onDragEnd = { onDragEnd() }
                    )
                }
            }
            .pointerInput(disabled) {
                if (!disabled) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { _, dragAmount -> 
                            onDragAmount(dragAmount.y) 
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
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
                    .clickable(enabled = !disabled, onClick = onToggleExpand)
            )

            if (panelState == BottomPanelState.COLLAPSED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        "上拉查看 HISTORY / STATS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

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
    var horizontalDragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.pointerInput(tab, enableHorizontalSwipe) {
            if (enableHorizontalSwipe) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDragAccumulator += dragAmount
                    },
                    onDragEnd = {
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

@Composable
private fun WeeklyChart(weeklyStats: List<TimerViewModel.WeeklyStat>, emphasize: Boolean = false) {
    if (weeklyStats.isEmpty()) return
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

private fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

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

private fun formatDurationHms(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatDate(epochMs: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun formatClock(epochMs: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}
