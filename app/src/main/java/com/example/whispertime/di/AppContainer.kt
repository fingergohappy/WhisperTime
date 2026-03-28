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

class AppContainer(context: Context) {
    private val database = WhisperTimeDatabase.getInstance(context)
    val projectDao: ProjectDao = database.projectDao()
    val timingRecordDao: TimingRecordDao = database.timingRecordDao()

    val projectRepository = ProjectRepository(projectDao)
    val timingRecordRepository = TimingRecordRepository(timingRecordDao)
    val timerEngine = TimerEngine()
    val activeTimerSessionStore = ActiveTimerSessionStore.fromContext(context)
    val voiceAnnouncementManager = VoiceAnnouncementManager(context).also { it.init() }
    val vibrationManager = VibrationManager(context)
}
