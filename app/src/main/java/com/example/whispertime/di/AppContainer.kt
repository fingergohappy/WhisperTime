package com.example.whispertime.di

import android.content.Context
import com.example.whispertime.data.local.WhisperTimeDatabase
import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.repository.ProjectRepository
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.timer.TimerEngine
import com.example.whispertime.tts.VoiceAnnouncementManager

/**
 * 应用程序的依赖注入 (DI) 容器。
 *
 * 负责管理应用范围内单例组件的生命周期，包括数据库、DAO、存储库和核心引擎。
 * 这种手动注入模式为应用提供了一个统一的依赖入口。
 */
class AppContainer(context: Context) {
    /**
     * Room 数据库实例。
     */
    private val database = WhisperTimeDatabase.getInstance(context)

    /**
     * 项目数据的 DAO。
     */
    val projectDao: ProjectDao = database.projectDao()

    /**
     * 计时记录数据的 DAO。
     */
    val timingRecordDao: TimingRecordDao = database.timingRecordDao()

    /**
     * 项目数据的存储库。
     */
    val projectRepository = ProjectRepository(projectDao)

    /**
     * 计时记录数据的存储库。
     */
    val timingRecordRepository = TimingRecordRepository(timingRecordDao)

    /**
     * 计时器核心引擎。
     */
    val timerEngine = TimerEngine()

    /**
     * 语音播报管理器。
     * 在创建时会自动调用 init() 进行初始化。
     */
    val voiceAnnouncementManager = VoiceAnnouncementManager(context).also { it.init() }
}
