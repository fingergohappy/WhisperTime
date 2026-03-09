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
import com.example.whispertime.data.repository.EditedField
import com.example.whispertime.data.repository.ProjectRepository
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.service.TimerForegroundService
import com.example.whispertime.timer.TimerConfig
import com.example.whispertime.timer.TimerEngine
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class TimerViewModel(
    private val projectId: Long,
    private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val timingRecordRepository: TimingRecordRepository,
    private val timerEngine: TimerEngine
) : ViewModel() {

    data class WeeklyStat(
        val label: String,
        val durationMs: Long
    )

    private val _projectName = MutableStateFlow("")
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    private val _config = MutableStateFlow<TimerConfig?>(null)
    val config: StateFlow<TimerConfig?> = _config.asStateFlow()

    val timerState: StateFlow<TimerState> = timerEngine.state
    val elapsedMs: StateFlow<Long> = timerEngine.elapsedMs
    val remainingMs: StateFlow<Long?> = timerEngine.remainingMs
    val prepareRemainingMs: StateFlow<Long?> = timerEngine.prepareRemainingMs

    private var project: ProjectEntity? = null
    val records: StateFlow<List<TimingRecordEntity>> = timingRecordRepository.getByProjectId(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allProjects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalDurationMs: StateFlow<Long> = timingRecordRepository.getTotalDuration(projectId)
        .map { it ?: 0L }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val recordCount: StateFlow<Int> = timingRecordRepository.getRecordCount(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val averageDurationMs: StateFlow<Long> = combine(totalDurationMs, recordCount) { total, count ->
        if (count > 0) total / count else 0L
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val weeklyStats: StateFlow<List<WeeklyStat>> = records
        .map { buildWeeklyStats(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
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
    }

    fun cancelTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_CANCEL
        }
        appContext.startService(intent)
    }

    fun deleteRecord(record: TimingRecordEntity) {
        viewModelScope.launch {
            timingRecordRepository.delete(record)
        }
    }

    fun updateRecordDurationSeconds(record: TimingRecordEntity, durationSeconds: Long) {
        viewModelScope.launch {
            timingRecordRepository.updateRecordWithLinkedFields(
                record = record,
                editedField = EditedField.DURATION_MS,
                newValue = durationSeconds.coerceAtLeast(1L) * 1000L
            )
        }
    }

    fun updateProjectConfig(
        mode: TimerMode,
        countdownSeconds: Long?,
        voiceIntervalSeconds: Long?,
        prepareSeconds: Long?
    ) {
        val currentProject = project ?: return

        viewModelScope.launch {
            val updatedProject = currentProject.copy(
                timerMode = mode.name,
                defaultDurationMs = if (mode == TimerMode.COUNTDOWN) {
                    countdownSeconds?.coerceAtLeast(1L)?.times(1_000L)
                } else {
                    null
                },
                voiceIntervalMs = voiceIntervalSeconds?.takeIf { it > 0L }?.times(1_000L),
                prepareTimeSeconds = prepareSeconds?.takeIf { it > 0L },
                updatedAt = System.currentTimeMillis()
            )

            projectRepository.updateProject(updatedProject)
            project = updatedProject
            _config.value = buildConfig(updatedProject)
        }
    }

    private fun loadProject() {
        viewModelScope.launch {
            projectRepository.getProjectById(projectId)
                .filterNotNull()
                .collect { loadedProject ->
                    project = loadedProject
                    _projectName.value = loadedProject.name
                    if (timerState.value == TimerState.IDLE) {
                        _config.value = buildConfig(loadedProject)
                    }
                }
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

    private fun String.toTimerMode(): TimerMode {
        return if (this == TimerMode.COUNTDOWN.name) {
            TimerMode.COUNTDOWN
        } else {
            TimerMode.COUNT_UP
        }
    }

    private fun buildWeeklyStats(records: List<TimingRecordEntity>): List<WeeklyStat> {
        val calendar = Calendar.getInstance()
        setToDayStart(calendar)
        val endOfToday = calendar.timeInMillis + DAY_MS
        val startOfWindow = calendar.timeInMillis - (6 * DAY_MS)

        val buckets = LongArray(7)
        records.forEach { record ->
            if (record.startTime < startOfWindow || record.startTime >= endOfToday) return@forEach
            val index = ((record.startTime - startOfWindow) / DAY_MS).toInt()
            if (index in buckets.indices) {
                buckets[index] += record.durationMs
            }
        }

        return (0..6).map { offset ->
            val dayCalendar = Calendar.getInstance().apply {
                timeInMillis = startOfWindow + offset * DAY_MS
            }
            WeeklyStat(
                label = DAY_LABELS[dayCalendar.get(Calendar.DAY_OF_WEEK)] ?: "",
                durationMs = buckets[offset]
            )
        }
    }

    private fun setToDayStart(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val DAY_LABELS = mapOf(
            Calendar.SUNDAY to "Sun",
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat"
        )
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
