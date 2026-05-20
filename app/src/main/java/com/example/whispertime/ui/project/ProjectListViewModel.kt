package com.example.whispertime.ui.project

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whispertime.WhisperTimeApplication
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 项目列表页状态管理，负责项目列表订阅和删除。 */
class ProjectListViewModel(
    /** 项目仓库。 */
    private val projectRepository: ProjectRepository
) : ViewModel() {

    /** 项目列表状态流，页面订阅后自动从 Room 收到更新。 */
    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 删除指定项目。 */
    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            projectRepository.deleteProject(project)
        }
    }

    /** ViewModel 工厂。 */
    companion object {
        /** 创建依赖应用容器的项目列表 ViewModel 工厂。 */
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                /** 从应用容器中取仓库并创建 ViewModel。 */
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as WhisperTimeApplication).container
                    return ProjectListViewModel(container.projectRepository) as T
                }
            }
    }
}
