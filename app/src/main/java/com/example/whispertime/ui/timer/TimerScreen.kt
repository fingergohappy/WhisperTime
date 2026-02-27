package com.example.whispertime.ui.timer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import java.util.Locale

/**
 * 计时工作台屏幕
 * 提供倒计时和正计时功能，支持自定义设置、准备时间以及实时语音播报反馈
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    projectId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToRecords: (Long) -> Unit,
    viewModel: TimerViewModel = viewModel(
        factory = TimerViewModel.factory(
            LocalContext.current.applicationContext as Application,
            projectId
        )
    )
) {
    val context = LocalContext.current
    // 观察计时器的各项状态
    val timerState by viewModel.timerState.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val remainingMs by viewModel.remainingMs.collectAsState()
    val projectName by viewModel.projectName.collectAsState()
    val config by viewModel.config.collectAsState()
    val prepareRemainingMs by viewModel.prepareRemainingMs.collectAsState()

    // 处理 Android 13+ 的通知权限请求
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No-op */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // IDLE 状态下的本地配置暂存状态
    var isConfigExpanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(TimerMode.COUNT_UP) }
    var durationMinutes by remember { mutableStateOf("25") }
    var intervalSeconds by remember { mutableStateOf("300") }
    var prepareSeconds by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    // 当从项目加载配置时，初始化本地暂存状态
    LaunchedEffect(config) {
        if (!initialized && config != null) {
            selectedMode = config!!.mode
            if (config!!.durationMs != null) {
                durationMinutes = (config!!.durationMs!! / 60000).toString()
            }
            if (config!!.voiceIntervalMs != null) {
                intervalSeconds = (config!!.voiceIntervalMs!! / 1000).toString()
            }
            if (config!!.prepareTimeMs != null) {
                prepareSeconds = (config!!.prepareTimeMs!! / 1000).toString()
            }
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(projectName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // 模式标签显示
            val currentMode = if (timerState == TimerState.IDLE) selectedMode else config?.mode ?: TimerMode.COUNT_UP
            Text(
                text = if (currentMode == TimerMode.COUNTDOWN) "倒计时" else "正计时",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // 时间显示区域
            val timeToShow = if (currentMode == TimerMode.COUNTDOWN) {
                remainingMs ?: (durationMinutes.toLongOrNull()?.times(60000) ?: 0L)
            } else {
                elapsedMs
            }
            
            Text(
                text = formatTime(timeToShow),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum" // 使用等宽数字，防止时间跳动
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 根据不同的计时器状态渲染不同的 UI
            if (timerState == TimerState.IDLE) {
                // 空闲状态：展示可展开的配置项及开始按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { isConfigExpanded = !isConfigExpanded }
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "自定义设置",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                imageVector = if (isConfigExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown,
                                contentDescription = if (isConfigExpanded) "Collapse" else "Expand"
                            )
                        }

                        AnimatedVisibility(
                            visible = isConfigExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                // 模式切换（正/倒计时）
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedMode == TimerMode.COUNT_UP,
                                        onClick = { selectedMode = TimerMode.COUNT_UP },
                                        label = { Text("正计时") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = selectedMode == TimerMode.COUNTDOWN,
                                        onClick = { selectedMode = TimerMode.COUNTDOWN },
                                        label = { Text("倒计时") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 时长输入（仅倒计时模式）
                                if (selectedMode == TimerMode.COUNTDOWN) {
                                    OutlinedTextField(
                                        value = durationMinutes,
                                        onValueChange = { if (it.all { char -> char.isDigit() }) durationMinutes = it },
                                        label = { Text("时长 (分钟)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // 语音播报间隔设置
                                OutlinedTextField(
                                    value = intervalSeconds,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) intervalSeconds = it },
                                    label = { Text("语音播报间隔 (秒)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 准备时间设置
                                OutlinedTextField(
                                    value = prepareSeconds,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) prepareSeconds = it },
                                    label = { Text("准备时间 (秒)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("可选") }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val duration = durationMinutes.toLongOrNull()?.times(60000L)
                        val interval = intervalSeconds.toLongOrNull()?.times(1000L)
                        val prepare = prepareSeconds.toLongOrNull()?.times(1000L)
                        viewModel.startTimer(
                            modeOverride = selectedMode,
                            durationOverride = duration,
                            intervalOverride = interval,
                            prepareOverrideMs = prepare
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("开始", style = MaterialTheme.typography.titleLarge)
                }
            } else if (timerState == TimerState.PREPARING) {
                // 准备状态：展示倒计时准备 UI
                Text(
                    text = "准备中...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = formatTime(ceilToSecondMs(prepareRemainingMs ?: 0L)),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { viewModel.cancelTimer() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("取消")
                }
            } else {
                // 运行中或暂停状态：展示操作按钮（暂停、继续、停止）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (timerState == TimerState.RUNNING) {
                        OutlinedButton(
                            onClick = { viewModel.pauseTimer() },
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Text("暂停")
                        }
                        
                        Button(
                            onClick = { 
                                viewModel.stopTimer()
                                onNavigateToRecords(projectId)
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("停止")
                        }
                    } else if (timerState == TimerState.PAUSED) {
                        Button(
                            onClick = { viewModel.resumeTimer() },
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Text("继续")
                        }
                        
                        OutlinedButton(
                            onClick = { 
                                viewModel.stopTimer()
                                onNavigateToRecords(projectId)
                            },
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Text("停止")
                        }
                    }
                }
                
                if (timerState == TimerState.PAUSED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { 
                            viewModel.cancelTimer()
                            onNavigateBack()
                        }
                    ) {
                        Text("取消计时", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun ceilToSecondMs(ms: Long): Long {
    if (ms <= 0L) return 0L
    return ((ms + 999L) / 1000L) * 1000L
}
