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

class RecordListViewModel(
    private val projectId: Long,
    private val timingRecordRepository: TimingRecordRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedRecordIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRecordIds: StateFlow<Set<Long>> = _selectedRecordIds.asStateFlow()

    val projectName: StateFlow<String> = projectRepository.getProjectById(projectId)
        .filterNotNull()
        .map { it.name }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val records: StateFlow<List<TimingRecordEntity>> =
        timingRecordRepository.getRecordsByProjectId(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalDurationMs: StateFlow<Long?> =
        timingRecordRepository.getTotalDurationByProjectId(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val recordCount: StateFlow<Int> =
        timingRecordRepository.getRecordCountByProjectId(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

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

    fun deleteRecord(record: TimingRecordEntity) {
        viewModelScope.launch {
            timingRecordRepository.delete(record)
        }
    }

    fun enterSelectionMode(initialRecordId: Long) {
        _isSelectionMode.value = true
        _selectedRecordIds.value = setOf(initialRecordId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedRecordIds.value = emptySet()
    }

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
