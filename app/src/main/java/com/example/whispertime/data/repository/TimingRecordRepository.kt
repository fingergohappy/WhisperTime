package com.example.whispertime.data.repository

import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.TimingRecordEntity
import kotlinx.coroutines.flow.Flow

/** 记录编辑时被用户直接修改的字段。 */
enum class EditedField {
    /** 用户修改了开始时间。 */
    START_TIME,

    /** 用户修改了结束时间。 */
    END_TIME,

    /** 用户修改了持续时长。 */
    DURATION_MS
}

/** 计时记录仓库，封装查询、统计和联动编辑规则。 */
class TimingRecordRepository(private val timingRecordDao: TimingRecordDao) {

    /** 获取指定项目下的全部计时记录。 */
    fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
        timingRecordDao.getByProjectId(projectId)

    /** 根据主键获取单条计时记录。 */
    fun getById(id: Long): Flow<TimingRecordEntity?> = timingRecordDao.getById(id)

    /** 新增计时记录并返回主键。 */
    suspend fun insert(record: TimingRecordEntity): Long = timingRecordDao.insert(record)

    /** 更新计时记录。 */
    suspend fun update(record: TimingRecordEntity) = timingRecordDao.update(record)

    /** 删除计时记录。 */
    suspend fun delete(record: TimingRecordEntity) = timingRecordDao.delete(record)

    /** 批量删除记录，空集合时直接返回避免无意义 SQL。 */
    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        timingRecordDao.deleteByIds(ids)
    }

    /** 统计指定项目的累计时长。 */
    fun getTotalDuration(projectId: Long): Flow<Long?> =
        timingRecordDao.getTotalDuration(projectId)

    /** 统计指定项目的记录数量。 */
    fun getRecordCount(projectId: Long): Flow<Int> =
        timingRecordDao.getRecordCount(projectId)

    /**
     * 按用户编辑字段联动更新计时记录。
     *
     * - 编辑 [EditedField.DURATION_MS]：结束时间 = 开始时间 + 新持续时长。
     * - 编辑 [EditedField.START_TIME]：保持结束时间不变，重新计算持续时长。
     * - 编辑 [EditedField.END_TIME]：保持开始时间不变，重新计算持续时长。
     *
     * 当计算得到的持续时长小于等于 0 时，统一钳制到最短 1000ms。
     */
    suspend fun updateRecordWithLinkedFields(
        record: TimingRecordEntity,
        editedField: EditedField,
        newValue: Long
    ) {
        val updated = when (editedField) {
            EditedField.DURATION_MS -> {
                // 时长由用户输入决定，结束时间跟随开始时间向后推。
                val clampedDuration = if (newValue <= 0) MIN_DURATION_MS else newValue
                record.copy(
                    durationMs = clampedDuration,
                    endTime = record.startTime + clampedDuration
                )
            }
            EditedField.START_TIME -> {
                // 开始时间前移/后移时优先保留原结束时间，避免用户丢失结束锚点。
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
                // 结束时间由用户输入决定，持续时长跟随开始时间重新计算。
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

    /** 仓库常量。 */
    companion object {
        /** 计时记录允许保存的最短持续时长。 */
        const val MIN_DURATION_MS = 1000L
    }
}
