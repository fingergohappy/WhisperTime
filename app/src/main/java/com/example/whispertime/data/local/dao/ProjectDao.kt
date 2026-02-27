package com.example.whispertime.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.whispertime.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * 项目实体的数据库访问接口。
 */
@Dao
interface ProjectDao {

    /**
     * 获取所有项目。
     * 按照最后修改时间降序排列。
     * @return 返回项目的 Flow 列表，观察者将收到更新通知。
     */
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ProjectEntity>>

    /**
     * 根据 ID 获取单个项目。
     * @param id 项目唯一标识
     * @return 返回该项目的 Flow，项目不存在时为 null。
     */
    @Query("SELECT * FROM projects WHERE id = :id")
    fun getById(id: Long): Flow<ProjectEntity?>

    /**
     * 插入一个新项目。
     * 如果存在冲突，则替换原有记录。
     * @param project 要插入的项目对象
     * @return 返回插入行的行 ID。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    /**
     * 更新一个现有项目。
     * @param project 要更新的项目对象
     */
    @Update
    suspend fun update(project: ProjectEntity)

    /**
     * 删除一个项目。
     * 级联删除相关的计时记录（由外键约束定义）。
     * @param project 要删除的项目对象
     */
    @Delete
    suspend fun delete(project: ProjectEntity)
}
