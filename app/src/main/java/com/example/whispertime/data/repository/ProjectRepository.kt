package com.example.whispertime.data.repository

import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * 项目数据的存储库类，负责协调来自不同数据源的数据。
 * 当前主要封装了 [ProjectDao] 的操作。
 */
class ProjectRepository(private val projectDao: ProjectDao) {
    /**
     * 获取所有项目流。
     */
    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAll()

    /**
     * 根据 ID 获取项目流。
     */
    fun getProjectById(id: Long): Flow<ProjectEntity?> = projectDao.getById(id)

    /**
     * 插入新项目。
     */
    suspend fun insertProject(project: ProjectEntity): Long = projectDao.insert(project)

    /**
     * 更新项目。
     */
    suspend fun updateProject(project: ProjectEntity) = projectDao.update(project)

    /**
     * 删除项目（会自动级联删除相关的计时记录）。
     */
    suspend fun deleteProject(project: ProjectEntity) = projectDao.delete(project)
}
