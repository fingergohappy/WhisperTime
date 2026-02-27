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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow

/**
 * 应用级依赖容器（占位实现）
 * 后续任务将逐步添加 Room、Repository、TimerEngine、TTS 等业务依赖
 */
class AppContainer(context: Context) {
    // 占位实现：后续任务将替换为真实依赖
    val projectRepository: ProjectRepository = ProjectRepository(object : ProjectDao {
        override fun getAll(): Flow<List<ProjectEntity>> = emptyFlow()
        override fun getById(id: Long): Flow<ProjectEntity?> = emptyFlow()
        override suspend fun insert(project: ProjectEntity): Long = 0L
        override suspend fun update(project: ProjectEntity) {}
        override suspend fun delete(project: ProjectEntity) {}
    })
    
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
