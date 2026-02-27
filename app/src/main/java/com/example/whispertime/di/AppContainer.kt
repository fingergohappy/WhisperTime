package com.example.whispertime.di

import android.content.Context
import com.example.whispertime.data.local.WhisperTimeDatabase
import com.example.whispertime.data.repository.ProjectRepository
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.timer.TimerEngine
import com.example.whispertime.tts.VoiceAnnouncementManager

class AppContainer(context: Context) {
    private val database: WhisperTimeDatabase = WhisperTimeDatabase.getInstance(context)

    val projectRepository: ProjectRepository = ProjectRepository(database.projectDao())

    val timingRecordRepository: TimingRecordRepository =
        TimingRecordRepository(database.timingRecordDao())

    val timerEngine: TimerEngine = TimerEngine()
    val voiceAnnouncementManager: VoiceAnnouncementManager = VoiceAnnouncementManager(context)
}
