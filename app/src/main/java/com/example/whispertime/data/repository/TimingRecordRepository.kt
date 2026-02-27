package com.example.whispertime.data.repository

import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.TimingRecordEntity
import kotlinx.coroutines.flow.Flow

enum class EditedField { START_TIME, END_TIME, DURATION_MS }

class TimingRecordRepository(private val timingRecordDao: TimingRecordDao) {

    fun getRecordsByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
        timingRecordDao.getByProjectId(projectId)

    fun getTotalDurationByProjectId(projectId: Long): Flow<Long?> =
        timingRecordDao.getTotalDuration(projectId)

    fun getRecordCountByProjectId(projectId: Long): Flow<Int> =
        timingRecordDao.getRecordCount(projectId)

    fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
        getRecordsByProjectId(projectId)

    fun getById(id: Long): Flow<TimingRecordEntity?> = timingRecordDao.getById(id)

    suspend fun insert(record: TimingRecordEntity): Long = timingRecordDao.insert(record)

    suspend fun update(record: TimingRecordEntity) = timingRecordDao.update(record)

    suspend fun delete(record: TimingRecordEntity) = timingRecordDao.delete(record)

    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        timingRecordDao.deleteByIds(ids)
    }

    fun getTotalDuration(projectId: Long): Flow<Long?> =
        getTotalDurationByProjectId(projectId)

    fun getRecordCount(projectId: Long): Flow<Int> =
        getRecordCountByProjectId(projectId)

    /**
     * Updates a timing record with linked-field adjustment.
     *
     * - If [EditedField.DURATION_MS] edited: endTime = startTime + newDurationMs
     * - If [EditedField.START_TIME] edited: durationMs = endTime - newStartTime (endTime fixed)
     * - If [EditedField.END_TIME] edited: durationMs = newEndTime - startTime (startTime fixed)
     *
     * If computed durationMs <= 0, clamps to minimum 1000ms and adjusts endTime accordingly.
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
