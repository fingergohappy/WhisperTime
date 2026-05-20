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

/** 记录编辑页状态管理，负责时间和持续时长的联动编辑。 */
class RecordEditViewModel(
    /** 需要编辑的记录主键。 */
    private val recordId: Long,
    /** 计时记录仓库。 */
    private val timingRecordRepository: TimingRecordRepository
) : ViewModel() {

    /** 原始记录快照，用于保存时 copy 更新。 */
    private var originalRecord: TimingRecordEntity? = null

    /** 开始时间输入文本。 */
    val startTimeText = MutableStateFlow("")

    /** 结束时间输入文本。 */
    val endTimeText = MutableStateFlow("")

    /** 持续时长输入文本，格式为 MM:SS。 */
    val durationText = MutableStateFlow("")

    /** 内部保存结果事件流。 */
    private val _saveResult = MutableSharedFlow<Boolean>()

    /** 对外暴露的保存结果事件流。 */
    val saveResult: SharedFlow<Boolean> = _saveResult.asSharedFlow()

    /** 页面输入和展示使用的日期时间格式。 */
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        viewModelScope.launch {
            val record = timingRecordRepository.getById(recordId).first()
            if (record != null) {
                // 初次加载时把时间戳转换成用户可编辑的文本格式。
                originalRecord = record
                startTimeText.value = dateTimeFormat.format(Date(record.startTime))
                endTimeText.value = dateTimeFormat.format(Date(record.endTime))
                durationText.value = formatDuration(record.durationMs)
            }
        }
    }

    /** 开始时间改变后，尝试根据开始和结束时间重算持续时长。 */
    fun onStartTimeChanged(text: String) {
        startTimeText.value = text
        updateDurationFromTimes()
    }

    /** 结束时间改变后，尝试根据开始和结束时间重算持续时长。 */
    fun onEndTimeChanged(text: String) {
        endTimeText.value = text
        updateDurationFromTimes()
    }

    /** 持续时长改变后，尝试根据开始时间推算结束时间。 */
    fun onDurationChanged(text: String) {
        durationText.value = text
        updateEndTimeFromDuration()
    }

    /** 根据开始时间和结束时间联动更新持续时长。 */
    private fun updateDurationFromTimes() {
        try {
            val start = dateTimeFormat.parse(startTimeText.value)?.time ?: return
            val end = dateTimeFormat.parse(endTimeText.value)?.time ?: return
            if (end > start) {
                val duration = end - start
                durationText.value = formatDuration(duration)
            }
        } catch (e: Exception) {
            // 用户输入过程中可能出现未完成日期格式，忽略解析错误等待继续输入。
        }
    }

    /** 根据开始时间和持续时长联动更新结束时间。 */
    private fun updateEndTimeFromDuration() {
        try {
            val start = dateTimeFormat.parse(startTimeText.value)?.time ?: return
            val durationMs = parseDuration(durationText.value) ?: return
            val end = start + durationMs
            endTimeText.value = dateTimeFormat.format(Date(end))
        } catch (e: Exception) {
            // 用户输入过程中可能出现未完成日期或时长格式，忽略解析错误。
        }
    }

    /** 校验并保存编辑后的记录。 */
    fun saveRecord() {
        viewModelScope.launch {
            val record = originalRecord ?: return@launch
            try {
                val start = dateTimeFormat.parse(startTimeText.value)?.time ?: throw IllegalArgumentException("Invalid start time")
                val end = dateTimeFormat.parse(endTimeText.value)?.time ?: throw IllegalArgumentException("Invalid end time")
                val duration = parseDuration(durationText.value) ?: (end - start)

                if (duration <= 0) throw IllegalArgumentException("Duration must be positive")

                // 保存时以开始和结束时间为准，保证三个字段最终一致。
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

    /** 将毫秒时长格式化为 MM:SS。 */
    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /** 解析 MM:SS 文本为毫秒时长。 */
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

    /** ViewModel 工厂。 */
    companion object {
        /** 创建携带 application 和 recordId 参数的工厂。 */
        fun factory(application: Application, recordId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                /** 从应用容器中取仓库并创建 ViewModel。 */
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
