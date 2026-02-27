package com.example.whispertime.di

import android.content.Context
import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.ProjectRepository
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.timer.TimerEngine
import com.example.whispertime.tts.VoiceAnnouncementManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * 应用级依赖容器（占位实现）
 * 后续任务将逐步添加 Room、Repository、TimerEngine、TTS 等业务依赖
 */
class AppContainer(context: Context) {
    private val projectState = MutableStateFlow<List<ProjectEntity>>(emptyList())
    private var nextProjectId = 1L

    private val projectDao: ProjectDao = object : ProjectDao {
        override fun getAll(): Flow<List<ProjectEntity>> = projectState.asStateFlow()

        override fun getById(id: Long): Flow<ProjectEntity?> = projectState.map { projects ->
            projects.firstOrNull { it.id == id }
        }

        override suspend fun insert(project: ProjectEntity): Long {
            val newId = if (project.id > 0L) project.id else nextProjectId++
            val saved = project.copy(id = newId)
            projectState.update { projects ->
                (projects.filterNot { it.id == saved.id } + saved).sortedByDescending { it.updatedAt }
            }
            return newId
        }

        override suspend fun update(project: ProjectEntity) {
            projectState.update { projects ->
                projects.map { existing ->
                    if (existing.id == project.id) project else existing
                }.sortedByDescending { it.updatedAt }
            }
        }

        override suspend fun delete(project: ProjectEntity) {
            projectState.update { projects ->
                projects.filterNot { it.id == project.id }
            }
        }
    }

    val projectRepository: ProjectRepository = ProjectRepository(projectDao)

    val timingRecordRepository: TimingRecordRepository = TimingRecordRepository(object : TimingRecordDao {
        override fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> = emptyFlow()
        override fun getById(id: Long): Flow<TimingRecordEntity?> = emptyFlow()
        override suspend fun insert(record: TimingRecordEntity): Long = 0L
        override suspend fun update(record: TimingRecordEntity) {}
        override suspend fun delete(record: TimingRecordEntity) {}
        override suspend fun deleteByIds(ids: List<Long>) {}
        override fun getTotalDuration(projectId: Long): Flow<Long?> = emptyFlow()
        override fun getRecordCount(projectId: Long): Flow<Int> = emptyFlow()
    })

    val timerEngine: TimerEngine = TimerEngine()
    val voiceAnnouncementManager: VoiceAnnouncementManager = VoiceAnnouncementManager(context)
}
