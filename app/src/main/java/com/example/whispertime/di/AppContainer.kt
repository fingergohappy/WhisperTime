package com.example.whispertime.di

import android.content.Context
import com.example.whispertime.data.local.WhisperTimeDatabase
import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.repository.ProjectRepository
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.service.ActiveTimerSessionStore
import com.example.whispertime.timer.TimerEngine
import com.example.whispertime.tts.VoiceAnnouncementManager
import com.example.whispertime.vibration.VibrationManager

/** 简单依赖容器，集中创建应用级单例对象。 */
class AppContainer(context: Context) {
    /** 本地 Room 数据库实例。 */
    private val database = WhisperTimeDatabase.getInstance(context)

    /** 项目 DAO，供仓库层使用。 */
    val projectDao: ProjectDao = database.projectDao()

    /** 计时记录 DAO，供仓库层使用。 */
    val timingRecordDao: TimingRecordDao = database.timingRecordDao()

    /** 项目仓库。 */
    val projectRepository = ProjectRepository(projectDao)

    /** 计时记录仓库。 */
    val timingRecordRepository = TimingRecordRepository(timingRecordDao)

    /** 全局计时引擎，保证前台服务和页面观察同一份状态。 */
    val timerEngine = TimerEngine()

    /** 活跃计时会话持久化仓库，用于进程恢复。 */
    val activeTimerSessionStore = ActiveTimerSessionStore.fromContext(context)

    /** 语音播报管理器，创建后立即初始化 TTS。 */
    val voiceAnnouncementManager = VoiceAnnouncementManager(context).also { it.init() }

    /** 震动提醒管理器。 */
    val vibrationManager = VibrationManager(context)
}
