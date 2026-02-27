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

/**
 * 历史记录列表 ViewModel
 * 处理历史记录的加载、统计逻辑以及多选删除功能
 */
class RecordListViewModel(
    private val projectId: Long,
    private val timingRecordRepository: TimingRecordRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _isSelectionMode = MutableStateFlow(false)
    /** 是否处于多选删除模式 */
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedRecordIds = MutableStateFlow<Set<Long>>(emptySet())
    /** 当前已选中的记录 ID 集合 */
    val selectedRecordIds: StateFlow<Set<Long>> = _selectedRecordIds.asStateFlow()

    /** 当前项目名称 */
    val projectName: StateFlow<String> = projectRepository.getProjectById(projectId)
        .filterNotNull()
        .map { it.name }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    /** 该项目下的所有计时记录 */
    val records: StateFlow<List<TimingRecordEntity>> = timingRecordRepository.getByProjectId(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 累计计时总时长（毫秒） */
    val totalDurationMs: StateFlow<Long?> = timingRecordRepository.getTotalDuration(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** 记录总条数 */
    val recordCount: StateFlow<Int> = timingRecordRepository.getRecordCount(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /** 平均计时时长（毫秒） */
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

    /** 删除单条记录 */
    fun deleteRecord(record: TimingRecordEntity) {
        viewModelScope.launch {
            timingRecordRepository.delete(record)
        }
    }

    /** 进入多选模式并选中首条记录 */
    fun enterSelectionMode(initialRecordId: Long) {
        _isSelectionMode.value = true
        _selectedRecordIds.value = setOf(initialRecordId)
    }

    /** 退出多选模式，清空选中项 */
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedRecordIds.value = emptySet()
    }

    /** 切换特定记录的选中状态 */
    fun toggleSelection(recordId: Long) {
        val current = _selectedRecordIds.value
        _selectedRecordIds.value = if (current.contains(recordId)) {
            current - recordId
        } else {
            current + recordId
        }
        // 如果没有选中项，自动退出多选模式
        if (_selectedRecordIds.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    /** 执行批量删除已选中的记录 */
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

    companion object {
        /** ViewModel 工厂方法，注入 Repository 及项目 ID */
        fun factory(application: Application, projectId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
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
