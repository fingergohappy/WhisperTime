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

/**
 * 项目列表 ViewModel
 * 负责从仓库获取项目数据，并处理删除项目的业务逻辑
 */
class ProjectListViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    /**
     * 所有项目的 StateFlow
     * 使用 WhileSubscribed(5000) 策略，在 UI 不可见 5 秒后停止订阅
     */
    val projects: StateFlow<List<ProjectEntity>> = projectRepository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 删除指定的项目
     * @param project 要删除的项目实体
     */
    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            projectRepository.deleteProject(project)
        }
    }

    companion object {
        /** ViewModel 工厂方法，注入必要的 Repository */
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as WhisperTimeApplication).container
                    return ProjectListViewModel(container.projectRepository) as T
                }
            }
    }
}
