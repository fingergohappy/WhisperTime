package com.example.whispertime.ui.record

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whispertime.WhisperTimeApplication
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.ProjectRepository
import com.example.whispertime.data.repository.TimingRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 记录列表页状态管理，负责统计、选择模式和批量删除。 */
class RecordListViewModel(
    /** 当前项目主键。 */
    private val projectId: Long,
    /** 计时记录仓库。 */
    private val timingRecordRepository: TimingRecordRepository,
    /** 项目仓库。 */
    private val projectRepository: ProjectRepository
) : ViewModel() {

    /** 内部选择模式状态。 */
    private val _isSelectionMode = MutableStateFlow(false)

    /** 是否正在批量选择记录。 */
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    /** 内部已选记录主键集合。 */
    private val _selectedRecordIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 对外暴露的已选记录主键集合。 */
    val selectedRecordIds: StateFlow<Set<Long>> = _selectedRecordIds.asStateFlow()

    /** 当前项目名称。 */
    val projectName: StateFlow<String> = projectRepository.getProjectById(projectId)
        .filterNotNull()
        .map { it.name }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    /** 当前项目的历史记录列表。 */
    val records: StateFlow<List<TimingRecordEntity>> = timingRecordRepository.getByProjectId(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 当前项目累计计时时长。 */
    val totalDurationMs: StateFlow<Long?> = timingRecordRepository.getTotalDuration(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** 当前项目记录数量。 */
    val recordCount: StateFlow<Int> = timingRecordRepository.getRecordCount(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /** 当前项目平均单次计时时长。 */
    val averageDurationMs: StateFlow<Long?> = combine(totalDurationMs, recordCount) { total, count ->
        if (total != null && count > 0) {
            total / count
        } else {
            null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /** 删除单条记录。 */
    fun deleteRecord(record: TimingRecordEntity) {
        viewModelScope.launch {
            timingRecordRepository.delete(record)
        }
    }

    /** 进入选择模式，并默认选中长按的记录。 */
    fun enterSelectionMode(initialRecordId: Long) {
        _isSelectionMode.value = true
        _selectedRecordIds.value = setOf(initialRecordId)
    }

    /** 退出选择模式并清空选择。 */
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedRecordIds.value = emptySet()
    }

    /** 切换单条记录的选中状态，全部取消时自动退出选择模式。 */
    fun toggleSelection(recordId: Long) {
        val current = _selectedRecordIds.value
        _selectedRecordIds.value = if (current.contains(recordId)) {
            current - recordId
        } else {
            current + recordId
        }
        if (_selectedRecordIds.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    /** 删除所有已选记录。 */
    fun deleteSelected() {
        val ids = _selectedRecordIds.value.toList()
        if (ids.isEmpty()) {
            exitSelectionMode()
            return
        }
        viewModelScope.launch {
            timingRecordRepository.deleteByIds(ids)
            exitSelectionMode()
        }
    }

    /** ViewModel 工厂。 */
    companion object {
        /** 创建携带 application 和 projectId 参数的工厂。 */
        fun factory(application: Application, projectId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                /** 从应用容器中取仓库并创建 ViewModel。 */
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as WhisperTimeApplication).container
                    return RecordListViewModel(
                        projectId = projectId,
                        timingRecordRepository = container.timingRecordRepository,
                        projectRepository = container.projectRepository
                    ) as T
                }
            }
    }
}
