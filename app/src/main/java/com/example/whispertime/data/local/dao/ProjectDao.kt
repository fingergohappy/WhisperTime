package com.example.whispertime.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.whispertime.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
/** 项目表 DAO，封装项目的查询和增删改操作。 */
interface ProjectDao {

    /** 按创建时间和主键顺序持续观察全部项目。 */
    @Query("SELECT * FROM projects ORDER BY createdAt ASC, id ASC")
    fun getAll(): Flow<List<ProjectEntity>>

    /** 根据项目主键持续观察单个项目。 */
    @Query("SELECT * FROM projects WHERE id = :id")
    fun getById(id: Long): Flow<ProjectEntity?>

    /** 插入或替换项目，并返回最终主键。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    /** 更新已有项目配置。 */
    @Update
    suspend fun update(project: ProjectEntity)

    /** 删除项目，关联记录由外键级联处理。 */
    @Delete
    suspend fun delete(project: ProjectEntity)
}
