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
/** 计时记录表 DAO，负责记录查询、统计和删除。 */
interface TimingRecordDao {

    /** 按开始时间倒序观察指定项目的计时记录。 */
    @Query("SELECT * FROM timing_records WHERE projectId = :projectId ORDER BY startTime DESC")
    fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>>

    /** 根据记录主键持续观察单条计时记录。 */
    @Query("SELECT * FROM timing_records WHERE id = :id")
    fun getById(id: Long): Flow<TimingRecordEntity?>

    /** 插入或替换计时记录，并返回记录主键。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TimingRecordEntity): Long

    /** 更新计时记录。 */
    @Update
    suspend fun update(record: TimingRecordEntity)

    /** 删除单条计时记录。 */
    @Delete
    suspend fun delete(record: TimingRecordEntity)

    /** 批量删除指定主键集合中的计时记录。 */
    @Query("DELETE FROM timing_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 统计指定项目的累计计时时长。 */
    @Query("SELECT SUM(durationMs) FROM timing_records WHERE projectId = :projectId")
    fun getTotalDuration(projectId: Long): Flow<Long?>

    /** 统计指定项目的记录数量。 */
    @Query("SELECT COUNT(*) FROM timing_records WHERE projectId = :projectId")
    fun getRecordCount(projectId: Long): Flow<Int>
}
