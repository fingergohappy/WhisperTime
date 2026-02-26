package com.example.whispertime.ui.record

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whispertime.WhisperTimeApplication
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.TimingRecordRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordEditViewModel(
    private val recordId: Long,
    private val timingRecordRepository: TimingRecordRepository
) : ViewModel() {

    private var originalRecord: TimingRecordEntity? = null

    val startTimeText = MutableStateFlow("")
    val endTimeText = MutableStateFlow("")
    val durationText = MutableStateFlow("")

    private val _saveResult = MutableSharedFlow<Boolean>()
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        viewModelScope.launch {
            val record = timingRecordRepository.getById(recordId).first()
            if (record != null) {
                originalRecord = record
                startTimeText.value = dateTimeFormat.format(Date(record.startTime))
                endTimeText.value = dateTimeFormat.format(Date(record.endTime))
                durationText.value = formatDuration(record.durationMs)
            }
        }
    }

    fun onStartTimeChanged(text: String) {
        startTimeText.value = text
        updateDurationFromTimes()
    }

    fun onEndTimeChanged(text: String) {
        endTimeText.value = text
        updateDurationFromTimes()
    }

    fun onDurationChanged(text: String) {
        durationText.value = text
        updateEndTimeFromDuration()
    }

    private fun updateDurationFromTimes() {
        try {
            val start = dateTimeFormat.parse(startTimeText.value)?.time ?: return
            val end = dateTimeFormat.parse(endTimeText.value)?.time ?: return
            if (end > start) {
                val duration = end - start
                durationText.value = formatDuration(duration)
            }
        } catch (e: Exception) {
            // Ignore parsing errors during typing
        }
    }

    private fun updateEndTimeFromDuration() {
        try {
            val start = dateTimeFormat.parse(startTimeText.value)?.time ?: return
            val durationMs = parseDuration(durationText.value) ?: return
            val end = start + durationMs
            endTimeText.value = dateTimeFormat.format(Date(end))
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    fun saveRecord() {
        viewModelScope.launch {
            val record = originalRecord ?: return@launch
            try {
                val start = dateTimeFormat.parse(startTimeText.value)?.time ?: throw IllegalArgumentException("Invalid start time")
                val end = dateTimeFormat.parse(endTimeText.value)?.time ?: throw IllegalArgumentException("Invalid end time")
                val duration = parseDuration(durationText.value) ?: (end - start)

                if (duration <= 0) throw IllegalArgumentException("Duration must be positive")

                // Ensure consistency: prioritize start and end times
                val finalDuration = end - start
                
                val updatedRecord = record.copy(
                    startTime = start,
                    endTime = end,
                    durationMs = finalDuration
                )
                
                timingRecordRepository.update(updatedRecord)
                _saveResult.emit(true)
            } catch (e: Exception) {
                e.printStackTrace()
                _saveResult.emit(false)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun parseDuration(text: String): Long? {
        return try {
            val parts = text.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].toLong()
                val seconds = parts[1].toLong()
                (minutes * 60 + seconds) * 1000
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun factory(application: Application, recordId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as WhisperTimeApplication).container
                    return RecordEditViewModel(
                        recordId = recordId,
                        timingRecordRepository = container.timingRecordRepository
                    ) as T
                }
            }
    }
}
