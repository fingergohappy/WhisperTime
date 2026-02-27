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

/**
 * 记录编辑界面的 ViewModel
 *
 * 负责解析和校验计时记录的时间（开始/结束）与时长，并处理它们之间的联动逻辑。
 *
 * @param recordId 要编辑的记录 ID
 * @param timingRecordRepository 计时记录数据仓库
 */
class RecordEditViewModel(
    private val recordId: Long,
    private val timingRecordRepository: TimingRecordRepository
) : ViewModel() {

    private var originalRecord: TimingRecordEntity? = null

    // UI 状态：开始时间文本 (格式: yyyy-MM-dd HH:mm:ss)
    val startTimeText = MutableStateFlow("")
    // UI 状态：结束时间文本 (格式: yyyy-MM-dd HH:mm:ss)
    val endTimeText = MutableStateFlow("")
    // UI 状态：时长文本 (格式: mm:ss)
    val durationText = MutableStateFlow("")

    // 操作结果事件流：保存是否成功
    private val _saveResult = MutableSharedFlow<Boolean>()
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        viewModelScope.launch {
            // 加载原始记录并初始化 UI 文本
            val record = timingRecordRepository.getById(recordId).first()
            if (record != null) {
                originalRecord = record
                startTimeText.value = dateTimeFormat.format(Date(record.startTime))
                endTimeText.value = dateTimeFormat.format(Date(record.endTime))
                durationText.value = formatDuration(record.durationMs)
            }
        }
    }

    /**
     * 当开始时间输入改变时触发
     */
    fun onStartTimeChanged(text: String) {
        startTimeText.value = text
        updateDurationFromTimes()
    }

    /**
     * 当结束时间输入改变时触发
     */
    fun onEndTimeChanged(text: String) {
        endTimeText.value = text
        updateDurationFromTimes()
    }

    /**
     * 当时长输入改变时触发
     */
    fun onDurationChanged(text: String) {
        durationText.value = text
        updateEndTimeFromDuration()
    }

    /**
     * 根据开始和结束时间自动计算并更新时长
     */
    private fun updateDurationFromTimes() {
        try {
            val start = dateTimeFormat.parse(startTimeText.value)?.time ?: return
            val end = dateTimeFormat.parse(endTimeText.value)?.time ?: return
            if (end > start) {
                val duration = end - start
                durationText.value = formatDuration(duration)
            }
        } catch (e: Exception) {
            // 忽略输入过程中的解析错误
        }
    }

    /**
     * 根据开始时间和时长自动计算并更新结束时间
     */
    private fun updateEndTimeFromDuration() {
        try {
            val start = dateTimeFormat.parse(startTimeText.value)?.time ?: return
            val durationMs = parseDuration(durationText.value) ?: return
            val end = start + durationMs
            endTimeText.value = dateTimeFormat.format(Date(end))
        } catch (e: Exception) {
            // 忽略解析错误
        }
    }

    /**
     * 保存记录修改
     */
    fun saveRecord() {
        viewModelScope.launch {
            val record = originalRecord ?: return@launch
            try {
                val start = dateTimeFormat.parse(startTimeText.value)?.time ?: throw IllegalArgumentException("Invalid start time")
                val end = dateTimeFormat.parse(endTimeText.value)?.time ?: throw IllegalArgumentException("Invalid end time")

                // 确保数据一致性：以开始和结束时间计算的结果为准
                val finalDuration = end - start
                if (finalDuration <= 0) throw IllegalArgumentException("Duration must be positive")

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

    /**
     * 将毫秒数格式化为 mm:ss 字符串
     */
    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /**
     * 将 mm:ss 字符串解析为毫秒数
     */
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
        /**
         * ViewModel 工厂方法
         */
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
