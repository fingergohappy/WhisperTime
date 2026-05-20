package com.example.whispertime.ui.project

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whispertime.WhisperTimeApplication
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** 项目编辑页状态管理，负责项目表单加载、保存和删除。 */
class ProjectEditViewModel(
    /** 需要编辑的项目主键，null 表示新建项目。 */
    private val projectId: Long?,
    /** 项目仓库。 */
    private val projectRepository: ProjectRepository
) : ViewModel() {

    /** 项目名称输入框状态。 */
    val projectName = MutableStateFlow("")

    /** 计时模式输入状态，值对应 TimerMode.name。 */
    val timerMode = MutableStateFlow("COUNT_UP")

    /** 倒计时默认时长输入状态，单位为分钟。 */
    val defaultDurationMinutes = MutableStateFlow("")

    /** 语音播报间隔输入状态，单位为秒。 */
    val voiceIntervalSeconds = MutableStateFlow("")

    /** 震动提醒开关状态。 */
    val vibrationEnabled = MutableStateFlow(false)

    /** 准备倒计时输入状态，单位为秒。 */
    val prepareTimeSeconds = MutableStateFlow("")

    /** 当前页面是否为编辑已有项目。 */
    val isEditMode: Boolean = projectId != null

    /** 内部保存结果事件流。 */
    private val _saveResult = MutableSharedFlow<Boolean>()

    /** 对外暴露的保存结果事件流，true 表示可以返回上一页。 */
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()

    init {
        if (projectId != null) {
            viewModelScope.launch {
                val project = projectRepository.getProjectById(projectId).firstOrNull()
                project?.let {
                    // 编辑模式下把数据库配置转换为表单可编辑的字符串。
                    projectName.value = it.name
                    timerMode.value = it.timerMode
                    defaultDurationMinutes.value = it.defaultDurationMs?.let { ms -> (ms / 60000).toString() } ?: ""
                    voiceIntervalSeconds.value = it.voiceIntervalMs?.let { ms -> (ms / 1000).toString() } ?: ""
                    vibrationEnabled.value = it.vibrationEnabled
                    prepareTimeSeconds.value = it.prepareTimeSeconds?.toString() ?: ""
                }
            }
        }
    }

    /** 校验表单并保存项目，新建和编辑共用同一套入口。 */
    fun saveProject() {
        val name = projectName.value.trim()
        val mode = timerMode.value
        val durationText = defaultDurationMinutes.value.trim()
        val intervalText = voiceIntervalSeconds.value.trim()
        val isVibrationEnabled = vibrationEnabled.value
        val prepareText = prepareTimeSeconds.value.trim()

        if (name.isEmpty()) return

        val durationMs = if (mode == "COUNTDOWN") {
            // 倒计时必须提供正数时长，正计时则不保存默认时长。
            val minutes = durationText.toLongOrNull()
            if (minutes == null || minutes <= 0) return
            minutes * 60000
        } else {
            null
        }

        val intervalMs = intervalText.toLongOrNull()?.let { it * 1000 }
        val prepareSeconds = prepareText.toLongOrNull()?.takeIf { it > 0 }

        viewModelScope.launch {
            if (isEditMode && projectId != null) {
                val original = projectRepository.getProjectById(projectId).firstOrNull()
                if (original != null) {
                    // 复制原实体可保留创建时间和主键，只覆盖用户编辑字段。
                    val updatedProject = original.copy(
                        name = name,
                        timerMode = mode,
                        defaultDurationMs = durationMs,
                        voiceIntervalMs = intervalMs,
                        vibrationEnabled = isVibrationEnabled,
                        prepareTimeSeconds = prepareSeconds,
                        updatedAt = System.currentTimeMillis()
                    )
                    projectRepository.updateProject(updatedProject)
                }
            } else {
                val newProject = ProjectEntity(
                    name = name,
                    timerMode = mode,
                    defaultDurationMs = durationMs,
                    voiceIntervalMs = intervalMs,
                    vibrationEnabled = isVibrationEnabled,
                    prepareTimeSeconds = prepareSeconds,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                projectRepository.insertProject(newProject)
            }
            _saveResult.emit(true)
        }
    }

    /** 删除当前项目；新建模式下等同于放弃并返回。 */
    fun deleteProject() {
        viewModelScope.launch {
            if (isEditMode && projectId != null) {
                val original = projectRepository.getProjectById(projectId).firstOrNull()
                if (original != null) {
                    projectRepository.deleteProject(original)
                }
            }
            _saveResult.emit(true)
        }
    }

    /** ViewModel 工厂。 */
    companion object {
        /** 创建携带 application 和 projectId 参数的工厂。 */
        fun factory(application: Application, projectId: Long?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                /** 从应用容器中取仓库并创建 ViewModel。 */
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as WhisperTimeApplication).container
                    return ProjectEditViewModel(projectId, container.projectRepository) as T
                }
            }
    }
}
