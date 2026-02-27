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

/**
 * 项目编辑 ViewModel
 * 负责管理项目创建/修改表单的状态，并处理持久化逻辑
 */
class ProjectEditViewModel(
    private val projectId: Long?,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    /** 项目名称 */
    val projectName = MutableStateFlow("")
    /** 计时模式：COUNT_UP 或 COUNTDOWN */
    val timerMode = MutableStateFlow("COUNT_UP")
    /** 默认时长（分钟） */
    val defaultDurationMinutes = MutableStateFlow("")
    /** 语音播报间隔（秒） */
    val voiceIntervalSeconds = MutableStateFlow("")
    /** 准备时间（秒） */
    val prepareTimeSeconds = MutableStateFlow("")

    /** 当前是否为编辑模式（而非创建新项目） */
    val isEditMode: Boolean = projectId != null

    private val _saveResult = MutableSharedFlow<Boolean>()
    /** 保存操作的结果流 */
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()

    init {
        // 如果是编辑模式，从仓库加载项目数据并初始化表单
        if (projectId != null) {
            viewModelScope.launch {
                val project = projectRepository.getProjectById(projectId).firstOrNull()
                project?.let {
                    projectName.value = it.name
                    timerMode.value = it.timerMode
                    defaultDurationMinutes.value = it.defaultDurationMs?.let { ms -> (ms / 60000).toString() } ?: ""
                    voiceIntervalSeconds.value = it.voiceIntervalMs?.let { ms -> (ms / 1000).toString() } ?: ""
                    prepareTimeSeconds.value = it.prepareTimeSeconds?.toString() ?: ""
                }
            }
        }
    }

    /**
     * 保存项目
     * 根据 isEditMode 执行更新或插入操作
     */
    fun saveProject() {
        val name = projectName.value.trim()
        val mode = timerMode.value
        val durationText = defaultDurationMinutes.value.trim()
        val intervalText = voiceIntervalSeconds.value.trim()
        val prepareText = prepareTimeSeconds.value.trim()

        if (name.isEmpty()) return

        // 校验并转换倒计时时长
        val durationMs = if (mode == "COUNTDOWN") {
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
                // 编辑模式：更新现有记录
                val original = projectRepository.getProjectById(projectId).firstOrNull()
                if (original != null) {
                    val updatedProject = original.copy(
                        name = name,
                        timerMode = mode,
                        defaultDurationMs = durationMs,
                        voiceIntervalMs = intervalMs,
                        prepareTimeSeconds = prepareSeconds,
                        updatedAt = System.currentTimeMillis()
                    )
                    projectRepository.updateProject(updatedProject)
                }
            } else {
                // 新建模式：插入新记录
                val newProject = ProjectEntity(
                    name = name,
                    timerMode = mode,
                    defaultDurationMs = durationMs,
                    voiceIntervalMs = intervalMs,
                    prepareTimeSeconds = prepareSeconds,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                projectRepository.insertProject(newProject)
            }
            // 通知 UI 保存成功
            _saveResult.emit(true)
        }
    }

    companion object {
        /** ViewModel 工厂方法，注入 Repository 及可选的项目 ID */
        fun factory(application: Application, projectId: Long?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as WhisperTimeApplication).container
                    return ProjectEditViewModel(projectId, container.projectRepository) as T
                }
            }
    }
}
