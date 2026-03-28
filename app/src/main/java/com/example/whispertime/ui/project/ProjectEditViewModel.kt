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

class ProjectEditViewModel(
    private val projectId: Long?,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    val projectName = MutableStateFlow("")
    val timerMode = MutableStateFlow("COUNT_UP")
    val defaultDurationMinutes = MutableStateFlow("")
    val voiceIntervalSeconds = MutableStateFlow("")
    val vibrationEnabled = MutableStateFlow(false)
    val prepareTimeSeconds = MutableStateFlow("")
    
    val isEditMode: Boolean = projectId != null

    private val _saveResult = MutableSharedFlow<Boolean>()
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()

    init {
        if (projectId != null) {
            viewModelScope.launch {
                val project = projectRepository.getProjectById(projectId).firstOrNull()
                project?.let {
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

    fun saveProject() {
        val name = projectName.value.trim()
        val mode = timerMode.value
        val durationText = defaultDurationMinutes.value.trim()
        val intervalText = voiceIntervalSeconds.value.trim()
        val isVibrationEnabled = vibrationEnabled.value
        val prepareText = prepareTimeSeconds.value.trim()

        if (name.isEmpty()) return

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
                val original = projectRepository.getProjectById(projectId).firstOrNull()
                if (original != null) {
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

    companion object {
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
