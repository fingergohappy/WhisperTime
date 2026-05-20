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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** 计时页状态管理，连接项目配置、计时服务、历史记录和统计数据。 */
class TimerViewModel(
    /** 当前项目主键。 */
    private val projectId: Long,
    /** 应用上下文，用于启动前台服务。 */
    private val appContext: Context,
    /** 项目仓库。 */
    private val projectRepository: ProjectRepository,
    /** 计时记录仓库。 */
    private val timingRecordRepository: TimingRecordRepository,
    /** 全局计时引擎。 */
    private val timerEngine: TimerEngine
) : ViewModel() {

    /** 一天的统计柱数据。 */
    data class WeeklyStat(
        /** 星期标签。 */
        val label: String,
        /** 当天累计时长。 */
        val durationMs: Long
    )

    /** 内部项目名称状态。 */
    private val _projectName = MutableStateFlow("")

    /** 当前项目名称。 */
    val projectName: StateFlow<String> = _projectName.asStateFlow()

    /** 内部当前计时配置状态。 */
    private val _config = MutableStateFlow<TimerConfig?>(null)

    /** 页面当前可用的计时配置。 */
    val config: StateFlow<TimerConfig?> = _config.asStateFlow()

    /** 内部项目删除结果事件。 */
    private val _deleteResult = MutableSharedFlow<Boolean>()

    /** 项目删除结果事件，true 表示页面应返回。 */
    val deleteResult: SharedFlow<Boolean> = _deleteResult.asSharedFlow()

    /** 计时状态，直接透传自全局计时引擎。 */
    val timerState: StateFlow<TimerState> = timerEngine.state

    /** 已计时时长，直接透传自全局计时引擎。 */
    val elapsedMs: StateFlow<Long> = timerEngine.elapsedMs

    /** 倒计时剩余时长，直接透传自全局计时引擎。 */
    val remainingMs: StateFlow<Long?> = timerEngine.remainingMs

    /** 准备倒计时剩余时长，直接透传自全局计时引擎。 */
    val prepareRemainingMs: StateFlow<Long?> = timerEngine.prepareRemainingMs

    /** 当前项目实体缓存，用于启动计时和保存配置。 */
    private var project: ProjectEntity? = null

    /** 当前项目的历史记录。 */
    val records: StateFlow<List<TimingRecordEntity>> = timingRecordRepository.getByProjectId(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 全部项目列表，用于计时页切换项目。 */
    val allProjects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 当前项目累计计时时长。 */
    val totalDurationMs: StateFlow<Long> = timingRecordRepository.getTotalDuration(projectId)
        .map { it ?: 0L }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /** 当前项目记录数量。 */
    val recordCount: StateFlow<Int> = timingRecordRepository.getRecordCount(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /** 当前项目平均单次计时时长。 */
    val averageDurationMs: StateFlow<Long> = combine(totalDurationMs, recordCount) { total, count ->
        if (count > 0) total / count else 0L
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    /** 最近七天的每日累计时长。 */
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

    /** 按当前项目和可选覆盖项启动前台计时服务。 */
    fun startTimer(
        modeOverride: TimerMode? = null,
        durationOverride: Long? = null,
        intervalOverride: Long? = null,
        vibrationOverride: Boolean? = null,
        prepareOverrideMs: Long? = null
    ) {
        val currentProject = project ?: return
        if (timerState.value != TimerState.IDLE) return

        val mode = modeOverride ?: currentProject.timerMode.toTimerMode()
        // 页面临时输入的配置优先于项目默认配置。
        val timerConfig = currentProject.toTimerConfig(
            modeOverride = mode,
            durationOverride = durationOverride,
            intervalOverride = intervalOverride,
            vibrationOverride = vibrationOverride,
            prepareOverrideMs = prepareOverrideMs
        )
        val durationMs = timerConfig.durationMs
        val intervalMs = timerConfig.voiceIntervalMs
        val prepareTimeMs = timerConfig.prepareTimeMs

        _config.value = timerConfig

        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_START
            putExtra(TimerForegroundService.EXTRA_PROJECT_ID, currentProject.id)
            putExtra(TimerForegroundService.EXTRA_PROJECT_NAME, currentProject.name)
            putExtra(TimerForegroundService.EXTRA_TIMER_MODE, mode.name)
            if (durationMs != null) putExtra(TimerForegroundService.EXTRA_DURATION_MS, durationMs)
            if (intervalMs != null) putExtra(TimerForegroundService.EXTRA_INTERVAL_MS, intervalMs)
            putExtra(TimerForegroundService.EXTRA_VIBRATION_ENABLED, timerConfig.vibrationEnabled)
            if (prepareTimeMs != null) putExtra(TimerForegroundService.EXTRA_PREPARE_MS, prepareTimeMs)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    /** 请求前台服务暂停计时。 */
    fun pauseTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_PAUSE
        }
        appContext.startService(intent)
    }

    /** 请求前台服务继续计时。 */
    fun resumeTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_RESUME
        }
        appContext.startService(intent)
    }

    /** 请求前台服务停止计时并保存记录。 */
    fun stopTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_STOP
        }
        appContext.startService(intent)
    }

    /** 请求前台服务取消计时且不保存记录。 */
    fun cancelTimer() {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_CANCEL
        }
        appContext.startService(intent)
    }

    /** 删除当前项目，若计时中则先取消计时。 */
    fun deleteCurrentProject() {
        val currentProject = project ?: return

        viewModelScope.launch {
            if (timerState.value != TimerState.IDLE) {
                cancelTimer()
            }
            projectRepository.deleteProject(currentProject)
            _deleteResult.emit(true)
        }
    }

    /** 删除单条计时记录。 */
    fun deleteRecord(record: TimingRecordEntity) {
        viewModelScope.launch {
            timingRecordRepository.delete(record)
        }
    }

    /** 更新记录时长，仓库层会联动结束时间。 */
    fun updateRecordDurationSeconds(record: TimingRecordEntity, durationSeconds: Long) {
        viewModelScope.launch {
            timingRecordRepository.updateRecordWithLinkedFields(
                record = record,
                editedField = EditedField.DURATION_MS,
                newValue = durationSeconds.coerceAtLeast(1L) * 1000L
            )
        }
    }

    /** 更新当前项目的默认计时配置。 */
    fun updateProjectConfig(
        mode: TimerMode,
        countdownSeconds: Long?,
        voiceIntervalSeconds: Long?,
        vibrationEnabled: Boolean,
        prepareSeconds: Long?
    ) {
        val currentProject = project ?: return

        viewModelScope.launch {
            // 倒计时时才保存默认时长，正计时模式下清空该字段。
            val updatedProject = currentProject.copy(
                timerMode = mode.name,
                defaultDurationMs = if (mode == TimerMode.COUNTDOWN) {
                    countdownSeconds?.coerceAtLeast(1L)?.times(1_000L)
                } else {
                    null
                },
                voiceIntervalMs = voiceIntervalSeconds?.takeIf { it > 0L }?.times(1_000L),
                vibrationEnabled = vibrationEnabled,
                prepareTimeSeconds = prepareSeconds?.takeIf { it > 0L },
                updatedAt = System.currentTimeMillis()
            )

            projectRepository.updateProject(updatedProject)
            project = updatedProject
            _config.value = buildConfig(updatedProject)
        }
    }

    /** 订阅项目详情并同步页面标题和默认配置。 */
    private fun loadProject() {
        viewModelScope.launch {
            projectRepository.getProjectById(projectId)
                .filterNotNull()
                .collect { loadedProject ->
                    project = loadedProject
                    _projectName.value = loadedProject.name
                    if (timerState.value == TimerState.IDLE) {
                        // 空闲时允许项目配置变化刷新表单；运行中保持当前计时配置不被覆盖。
                        _config.value = buildConfig(loadedProject)
                    }
                }
        }
    }

    /** 根据项目和覆盖项构建计时配置。 */
    private fun buildConfig(
        project: ProjectEntity,
        modeOverride: TimerMode? = null,
        durationOverride: Long? = null,
        intervalOverride: Long? = null,
        prepareOverrideMs: Long? = null
    ): TimerConfig = project.toTimerConfig(
        modeOverride = modeOverride,
        durationOverride = durationOverride,
        intervalOverride = intervalOverride,
        prepareOverrideMs = prepareOverrideMs
    )

    /** 将持久化字符串转换为计时模式，异常值默认正计时。 */
    private fun String.toTimerMode(): TimerMode {
        return if (this == TimerMode.COUNTDOWN.name) {
            TimerMode.COUNTDOWN
        } else {
            TimerMode.COUNT_UP
        }
    }

    /** 汇总最近七天每天的计时时长。 */
    private fun buildWeeklyStats(records: List<TimingRecordEntity>): List<WeeklyStat> {
        val calendar = Calendar.getInstance()
        setToDayStart(calendar)
        val endOfToday = calendar.timeInMillis + DAY_MS
        val startOfWindow = calendar.timeInMillis - (6 * DAY_MS)

        val buckets = LongArray(7)
        records.forEach { record ->
            if (record.startTime < startOfWindow || record.startTime >= endOfToday) return@forEach
            // 记录按开始时间落入最近七天窗口中的一天。
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

    /** 把 Calendar 调整到当天 00:00:00.000。 */
    private fun setToDayStart(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    /** ViewModel 工厂和统计常量。 */
    companion object {
        /** 一天的毫秒数。 */
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** 星期数字到短标签的映射。 */
        private val DAY_LABELS = mapOf(
            Calendar.SUNDAY to "Sun",
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat"
        )

        /** 创建携带 application 和 projectId 参数的工厂。 */
        fun factory(application: Application, projectId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                /** 从应用容器中取依赖并创建 ViewModel。 */
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

/** 将项目实体转换为一次计时启动配置。 */
internal fun ProjectEntity.toTimerConfig(
    modeOverride: TimerMode? = null,
    durationOverride: Long? = null,
    intervalOverride: Long? = null,
    vibrationOverride: Boolean? = null,
    prepareOverrideMs: Long? = null
): TimerConfig {
    // 覆盖项来自页面临时设置，缺省时回退到项目保存的默认配置。
    val mode = modeOverride ?: if (timerMode == TimerMode.COUNTDOWN.name) {
        TimerMode.COUNTDOWN
    } else {
        TimerMode.COUNT_UP
    }
    val durationMs = if (mode == TimerMode.COUNTDOWN) {
        // 正计时不需要 duration，倒计时优先使用临时输入时长。
        durationOverride ?: defaultDurationMs
    } else {
        null
    }

    return TimerConfig(
        projectId = id,
        projectName = name,
        mode = mode,
        durationMs = durationMs,
        voiceIntervalMs = intervalOverride ?: voiceIntervalMs,
        vibrationEnabled = vibrationOverride ?: vibrationEnabled,
        prepareTimeMs = prepareOverrideMs ?: prepareTimeSeconds?.let { it * 1000L }
    )
}
