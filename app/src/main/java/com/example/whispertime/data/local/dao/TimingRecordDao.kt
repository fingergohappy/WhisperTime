package com.example.whispertime.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.whispertime.data.local.entity.TimingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimingRecordDao {

    @Query("SELECT * FROM timing_records WHERE projectId = :projectId ORDER BY startTime DESC")
    fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>>

    @Query("SELECT * FROM timing_records WHERE id = :id")
    fun getById(id: Long): Flow<TimingRecordEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TimingRecordEntity): Long

    @Update
    suspend fun update(record: TimingRecordEntity)

    @Delete
    suspend fun delete(record: TimingRecordEntity)

    @Query("DELETE FROM timing_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT SUM(durationMs) FROM timing_records WHERE projectId = :projectId")
    fun getTotalDuration(projectId: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM timing_records WHERE projectId = :projectId")
    fun getRecordCount(projectId: Long): Flow<Int>
}
