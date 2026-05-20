package com.example.whispertime.data.repository

import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/** 项目仓库，隔离上层 ViewModel 与 Room DAO。 */
class ProjectRepository(private val projectDao: ProjectDao) {
    /** 获取全部项目的响应式数据流。 */
    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAll()

    /** 根据主键获取项目的响应式数据流。 */
    fun getProjectById(id: Long): Flow<ProjectEntity?> = projectDao.getById(id)

    /** 新增项目并返回主键。 */
    suspend fun insertProject(project: ProjectEntity): Long = projectDao.insert(project)

    /** 更新项目配置。 */
    suspend fun updateProject(project: ProjectEntity) = projectDao.update(project)

    /** 删除项目及其级联记录。 */
    suspend fun deleteProject(project: ProjectEntity) = projectDao.delete(project)
}
