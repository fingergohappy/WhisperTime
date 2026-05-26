package com.example.whispertime.ui.timer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whispertime.ui.timer.screen.requestBatteryOptimizationExemptionIfNeeded

/** 计时主页面，承载计时圆盘、项目切换、设置、历史记录和统计面板。 */
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

    // 项目被删除后返回上一页。
    LaunchedEffect(Unit) {
        viewModel.deleteResult.collect { deleted ->
            if (deleted) {
                onNavigateBack()
            }
        }
    }

    TimerScreenContent(
        projectId = projectId,
        timerState = timerState,
        elapsedMs = elapsedMs,
        remainingMs = remainingMs,
        prepareRemainingMs = prepareRemainingMs,
        config = config,
        projectName = projectName,
        projects = projects,
        records = records,
        totalDurationMs = totalDurationMs,
        recordCount = recordCount,
        averageDurationMs = averageDurationMs,
        weeklyStats = weeklyStats,
        onNavigateToTimer = onNavigateToTimer,
        onNavigateToCreateProject = onNavigateToCreateProject,
        onUpdateProjectConfig = viewModel::updateProjectConfig,
        onCancelTimer = viewModel::cancelTimer,
        onPauseTimer = viewModel::pauseTimer,
        onResumeTimer = viewModel::resumeTimer,
        onStartTimer = viewModel::startTimer,
        onStopTimer = viewModel::stopTimer,
        onDeleteCurrentProject = viewModel::deleteCurrentProject,
        onDeleteRecord = viewModel::deleteRecord,
        onUpdateRecordDurationSeconds = viewModel::updateRecordDurationSeconds
    )
}
