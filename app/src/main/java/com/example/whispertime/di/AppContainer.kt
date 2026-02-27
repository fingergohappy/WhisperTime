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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * 应用级依赖容器（占位实现）
 * 后续任务将逐步添加 Room、Repository、TimerEngine、TTS 等业务依赖
 */
class AppContainer(context: Context) {
    private val projectState = MutableStateFlow<List<ProjectEntity>>(emptyList())
    private val timingRecordState = MutableStateFlow<List<TimingRecordEntity>>(emptyList())
    private var nextProjectId = 1L
    private var nextTimingRecordId = 1L

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

    private val timingRecordDao: TimingRecordDao = object : TimingRecordDao {
        override fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
            timingRecordState.map { records ->
                records.filter { it.projectId == projectId }.sortedByDescending { it.startTime }
            }

        override fun getById(id: Long): Flow<TimingRecordEntity?> = timingRecordState.map { records ->
            records.firstOrNull { it.id == id }
        }

        override suspend fun insert(record: TimingRecordEntity): Long {
            val newId = if (record.id > 0L) record.id else nextTimingRecordId++
            val saved = record.copy(id = newId)
            timingRecordState.update { records ->
                (records.filterNot { it.id == saved.id } + saved).sortedByDescending { it.startTime }
            }
            return newId
        }

        override suspend fun update(record: TimingRecordEntity) {
            timingRecordState.update { records ->
                records.map { existing ->
                    if (existing.id == record.id) record else existing
                }.sortedByDescending { it.startTime }
            }
        }

        override suspend fun delete(record: TimingRecordEntity) {
            timingRecordState.update { records ->
                records.filterNot { it.id == record.id }
            }
        }

        override suspend fun deleteByIds(ids: List<Long>) {
            if (ids.isEmpty()) return
            timingRecordState.update { records ->
                records.filterNot { it.id in ids }
            }
        }

        override fun getTotalDuration(projectId: Long): Flow<Long?> = timingRecordState.map { records ->
            val durations = records.asSequence().filter { it.projectId == projectId }.map { it.durationMs }
            durations.sum().takeIf { total -> total > 0L }
        }

        override fun getRecordCount(projectId: Long): Flow<Int> = timingRecordState.map { records ->
            records.count { it.projectId == projectId }
        }
    }

    val timingRecordRepository: TimingRecordRepository = TimingRecordRepository(timingRecordDao)

    val timerEngine: TimerEngine = TimerEngine()
    val voiceAnnouncementManager: VoiceAnnouncementManager = VoiceAnnouncementManager(context)
}
