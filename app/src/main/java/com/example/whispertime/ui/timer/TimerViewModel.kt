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

class TimerViewModel(
    private val projectId: Long,
    private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val timingRecordRepository: TimingRecordRepository,
    private val timerEngine: TimerEngine
) : ViewModel() {

    private val _projectName = MutableStateFlow("")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _config = MutableStateFlow<TimerConfig?>(null)
    val config: StateFlow<TimerConfig?> = _config.asStateFlow()

    val timerState: StateFlow<TimerState> = timerEngine.state
    val elapsedMs: StateFlow<Long> = timerEngine.elapsedMs
    val remainingMs: StateFlow<Long?> = timerEngine.remainingMs
    val prepareRemainingMs: StateFlow<Long?> = timerEngine.prepareRemainingMs
    val shouldAnnounce: SharedFlow<Long> = timerEngine.shouldAnnounce

    private var project: ProjectEntity? = null
    private var activeTimerConfig: TimerConfig? = null
    private var activeStartEpoch: Long = 0L

    init {
        observeCompletionAnnouncements()
        loadProject()
    }

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

    fun pauseTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_PAUSE
        }
        appContext.startService(intent)
    }

    fun resumeTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_RESUME
        }
        appContext.startService(intent)
    }

    fun stopTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_STOP
        }
        appContext.startService(intent)
        clearActiveTimer()
    }

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
