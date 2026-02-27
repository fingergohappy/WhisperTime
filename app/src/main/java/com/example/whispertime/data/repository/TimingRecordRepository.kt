package com.example.whispertime.data.repository

import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.TimingRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 计时记录编辑时的字段类型枚举。
 */
enum class EditedField { START_TIME, END_TIME, DURATION_MS }

/**
 * 计时记录数据的存储库类。
 * 除了基础的 CRUD 外，还包含处理开始/结束时间与时长之间联动逻辑的业务方法。
 */
class TimingRecordRepository(private val timingRecordDao: TimingRecordDao) {

    /**
     * 获取指定项目的计时记录流。
     */
    fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
        timingRecordDao.getByProjectId(projectId)

    /**
     * 根据 ID 获取计时记录流。
     */
    fun getById(id: Long): Flow<TimingRecordEntity?> = timingRecordDao.getById(id)

    /**
     * 插入计时记录。
     */
    suspend fun insert(record: TimingRecordEntity): Long = timingRecordDao.insert(record)

    /**
     * 更新计时记录。
     */
    suspend fun update(record: TimingRecordEntity) = timingRecordDao.update(record)

    /**
     * 删除计时记录。
     */
    suspend fun delete(record: TimingRecordEntity) = timingRecordDao.delete(record)

    /**
     * 批量删除计时记录。
     */
    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        timingRecordDao.deleteByIds(ids)
    }

    /**
     * 获取项目累计总时长。
     */
    fun getTotalDuration(projectId: Long): Flow<Long?> =
        timingRecordDao.getTotalDuration(projectId)

    /**
     * 获取项目累计次数。
     */
    fun getRecordCount(projectId: Long): Flow<Int> =
        timingRecordDao.getRecordCount(projectId)

    /**
     * 更新计时记录并处理联动字段。
     *
     * 联动规则如下：
     * - 如果编辑了 [EditedField.DURATION_MS]：endTime = startTime + newDurationMs
     * - 如果编辑了 [EditedField.START_TIME]：durationMs = endTime - newStartTime (endTime 保持不变)
     * - 如果编辑了 [EditedField.END_TIME]：durationMs = newEndTime - startTime (startTime 保持不变)
     *
     * 如果计算出的时长 durationMs <= 0，则强制设为最小 1000ms，并相应调整 endTime。
     *
     * @param record 当前记录对象
     * @param editedField 被修改的字段
     * @param newValue 字段的新值
     */
    suspend fun updateRecordWithLinkedFields(
        record: TimingRecordEntity,
        editedField: EditedField,
        newValue: Long
    ) {
        val updated = when (editedField) {
            EditedField.DURATION_MS -> {
                val clampedDuration = if (newValue <= 0) MIN_DURATION_MS else newValue
                record.copy(
                    durationMs = clampedDuration,
                    endTime = record.startTime + clampedDuration
                )
            }
            EditedField.START_TIME -> {
                val computedDuration = record.endTime - newValue
                if (computedDuration <= 0) {
                    record.copy(
                        startTime = newValue,
                        durationMs = MIN_DURATION_MS,
                        endTime = newValue + MIN_DURATION_MS
                    )
                } else {
                    record.copy(
                        startTime = newValue,
                        durationMs = computedDuration
                    )
                }
            }
            EditedField.END_TIME -> {
                val computedDuration = newValue - record.startTime
                if (computedDuration <= 0) {
                    record.copy(
                        endTime = record.startTime + MIN_DURATION_MS,
                        durationMs = MIN_DURATION_MS
                    )
                } else {
                    record.copy(
                        endTime = newValue,
                        durationMs = computedDuration
                    )
                }
            }
        }
        timingRecordDao.update(updated)
    }

    companion object {
        const val MIN_DURATION_MS = 1000L
    }
}
