package com.example.whispertime.ui.timer

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whispertime.WhisperTimeApplication
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.ProjectRepository
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.service.TimerForegroundService
import com.example.whispertime.timer.TimerConfig
import com.example.whispertime.timer.TimerEngine
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerResult
import com.example.whispertime.timer.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * 计时工作台 ViewModel
 * 桥接 UI 与计时引擎及后台服务，管理计时的生命周期、配置以及结果保存
 */
class TimerViewModel(
    private val projectId: Long,
    private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val timingRecordRepository: TimingRecordRepository,
    private val timerEngine: TimerEngine
) : ViewModel() {

    private val _projectName = MutableStateFlow("")
    /** 当前项目名称 */
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _config = MutableStateFlow<TimerConfig?>(null)
    /** 当前计时配置 */
    val config: StateFlow<TimerConfig?> = _config.asStateFlow()

    /** 计时引擎状态（IDLE, PREPARING, RUNNING, PAUSED） */
    val timerState: StateFlow<TimerState> = timerEngine.state
    /** 已用时长（毫秒） */
    val elapsedMs: StateFlow<Long> = timerEngine.elapsedMs
    /** 剩余时长（倒计时模式，毫秒） */
    val remainingMs: StateFlow<Long?> = timerEngine.remainingMs
    /** 准备阶段剩余时长（毫秒） */
    val prepareRemainingMs: StateFlow<Long?> = timerEngine.prepareRemainingMs
    /** 语音播报触发信号（发送已用时长） */
    val shouldAnnounce: SharedFlow<Long> = timerEngine.shouldAnnounce

    private var project: ProjectEntity? = null
    private var activeTimerConfig: TimerConfig? = null
    private var activeStartEpoch: Long = 0L

    init {
        // 监听计时完成信号，以便保存记录
        observeCompletionAnnouncements()
        // 加载项目基础数据
        loadProject()
    }

    /**
     * 启动计时
     * 如果提供了 override 参数，则优先使用 override 值，否则使用项目默认配置
     */
    fun startTimer(
        modeOverride: TimerMode? = null,
        durationOverride: Long? = null,
        intervalOverride: Long? = null,
        prepareOverrideMs: Long? = null
    ) {
        val currentProject = project ?: return
        if (timerState.value != TimerState.IDLE) return

        val mode = modeOverride ?: currentProject.timerMode.toTimerMode()
        val durationMs = if (mode == TimerMode.COUNTDOWN) {
            durationOverride ?: currentProject.defaultDurationMs
        } else {
            null
        }
        val intervalMs = intervalOverride ?: currentProject.voiceIntervalMs
        val prepareTimeMs = prepareOverrideMs ?: currentProject.prepareTimeSeconds?.let { it * 1000L }
        val timerConfig = TimerConfig(
            projectId = currentProject.id,
            projectName = currentProject.name,
            mode = mode,
            durationMs = durationMs,
            voiceIntervalMs = intervalMs,
            prepareTimeMs = prepareTimeMs
        )

        _config.value = timerConfig
        activeTimerConfig = timerConfig
        activeStartEpoch = System.currentTimeMillis()

        // 通过 Intent 启动前台服务，确保计时在后台持续运行
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_START
            putExtra(TimerForegroundService.EXTRA_PROJECT_ID, currentProject.id)
            putExtra(TimerForegroundService.EXTRA_PROJECT_NAME, currentProject.name)
            putExtra(TimerForegroundService.EXTRA_TIMER_MODE, mode.name)
            if (durationMs != null) putExtra(TimerForegroundService.EXTRA_DURATION_MS, durationMs)
            if (intervalMs != null) putExtra(TimerForegroundService.EXTRA_INTERVAL_MS, intervalMs)
            if (prepareTimeMs != null) putExtra(TimerForegroundService.EXTRA_PREPARE_MS, prepareTimeMs)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    /** 暂停计时 */
    fun pauseTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_PAUSE
        }
        appContext.startService(intent)
    }

    /** 恢复计时 */
    fun resumeTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_RESUME
        }
        appContext.startService(intent)
    }

    /** 停止并保存计时（正常结束） */
    fun stopTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_STOP
        }
        appContext.startService(intent)
        clearActiveTimer()
    }

    /** 取消计时（不保存记录） */
    fun cancelTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_CANCEL
        }
        appContext.startService(intent)
        clearActiveTimer()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val loadedProject = projectRepository.getProjectById(projectId).firstOrNull() ?: return@launch
            project = loadedProject
            _projectName.value = loadedProject.name
            _config.value = buildConfig(loadedProject)
        }
    }

    private fun buildConfig(
        project: ProjectEntity,
        modeOverride: TimerMode? = null,
        durationOverride: Long? = null,
        intervalOverride: Long? = null,
        prepareOverrideMs: Long? = null
    ): TimerConfig {
        val mode = modeOverride ?: project.timerMode.toTimerMode()
        val durationMs = if (mode == TimerMode.COUNTDOWN) {
            durationOverride ?: project.defaultDurationMs
        } else {
            null
        }

        return TimerConfig(
            projectId = project.id,
            projectName = project.name,
            mode = mode,
            durationMs = durationMs,
            voiceIntervalMs = intervalOverride ?: project.voiceIntervalMs,
            prepareTimeMs = prepareOverrideMs ?: project.prepareTimeSeconds?.let { it * 1000L }
        )
    }

    /**
     * 监听计时完成信号
     * 当接收到 signal == -1L 时，表示计时正常结束或倒计时归零
     */
    private fun observeCompletionAnnouncements() {
        viewModelScope.launch {
            shouldAnnounce.collect { signal ->
                if (signal != -1L) return@collect

                val currentConfig = activeTimerConfig ?: return@collect
                val durationMs = currentConfig.durationMs ?: elapsedMs.value
                val endEpoch = System.currentTimeMillis()
                val startEpoch = if (activeStartEpoch > 0L) {
                    activeStartEpoch
                } else {
                    endEpoch - durationMs
                }

                // 保存本次计时的历史记录
                saveRecord(
                    TimerResult(
                        projectId = currentConfig.projectId,
                        startTimeEpoch = startEpoch,
                        endTimeEpoch = endEpoch,
                        durationMs = durationMs
                    )
                )
                clearActiveTimer()
            }
        }
    }

    /** 将计时结果持久化到数据库 */
    private suspend fun saveRecord(result: TimerResult) {
        val record = TimingRecordEntity(
            projectId = result.projectId,
            startTime = result.startTimeEpoch,
            endTime = result.endTimeEpoch,
            durationMs = result.durationMs,
            createdAt = System.currentTimeMillis()
        )
        timingRecordRepository.insert(record)
    }

    private fun clearActiveTimer() {
        activeTimerConfig = null
        activeStartEpoch = 0L
    }

    private fun String.toTimerMode(): TimerMode {
        return if (this == TimerMode.COUNTDOWN.name) {
            TimerMode.COUNTDOWN
        } else {
            TimerMode.COUNT_UP
        }
    }

    companion object {
        /** ViewModel 工厂方法，注入 Repository 和计时引擎 */
        fun factory(application: Application, projectId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as WhisperTimeApplication).container
                    return TimerViewModel(
                        projectId = projectId,
                        appContext = application.applicationContext,
                        projectRepository = container.projectRepository,
                        timingRecordRepository = container.timingRecordRepository,
                        timerEngine = container.timerEngine
                    ) as T
                }
            }
    }
}
