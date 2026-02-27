package com.example.whispertime.data.repository

import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {

    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAll()

    fun getProjectById(id: Long): Flow<ProjectEntity?> = projectDao.getById(id)

    suspend fun insertProject(project: ProjectEntity): Long = projectDao.insert(project)

    suspend fun updateProject(project: ProjectEntity) = projectDao.update(project)

    suspend fun deleteProject(project: ProjectEntity) = projectDao.delete(project)
}
