package com.example.whispertime.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.whispertime.data.local.entity.TimingRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 计时记录实体的数据库访问接口。
 */
@Dao
interface TimingRecordDao {

    /**
     * 获取指定项目下的所有计时记录。
     * 按开始时间降序排列。
     */
    @Query("SELECT * FROM timing_records WHERE projectId = :projectId ORDER BY startTime DESC")
    fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>>

    /**
     * 根据 ID 获取单个计时记录。
     */
    @Query("SELECT * FROM timing_records WHERE id = :id")
    fun getById(id: Long): Flow<TimingRecordEntity?>

    /**
     * 插入新的计时记录。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TimingRecordEntity): Long

    /**
     * 更新计时记录。
     */
    @Update
    suspend fun update(record: TimingRecordEntity)

    /**
     * 删除单条计时记录。
     */
    @Delete
    suspend fun delete(record: TimingRecordEntity)

    /**
     * 批量删除计时记录。
     */
    @Query("DELETE FROM timing_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * 获取特定项目的累计总时长。
     */
    @Query("SELECT SUM(durationMs) FROM timing_records WHERE projectId = :projectId")
    fun getTotalDuration(projectId: Long): Flow<Long?>

    /**
     * 获取特定项目的累计计次。
     */
    @Query("SELECT COUNT(*) FROM timing_records WHERE projectId = :projectId")
    fun getRecordCount(projectId: Long): Flow<Int>
}
