package com.example.whispertime.service

import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.whispertime.R
import com.example.whispertime.WhisperTimeApplication
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.timer.TimerConfig
import com.example.whispertime.timer.TimerEngine
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.tts.VoiceAnnouncementManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TimerForegroundService : Service() {
    private var timerStatus: TimerStatus = TimerStatus.IDLE
    private var currentProjectName: String = ""
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var timerEngine: TimerEngine
    private lateinit var timingRecordRepository: TimingRecordRepository
    private lateinit var voiceAnnouncementManager: VoiceAnnouncementManager
    private var completionJob: Job? = null
    private var prepareAnnouncementJob: Job? = null
    private var activeProjectId: Long? = null
    private var activeDurationMs: Long? = null
    private var activeTimerMode: TimerMode = TimerMode.COUNT_UP
    private var activeStartEpoch: Long = 0L
    private var completionRecorded: Boolean = false
    private var lastPrepareAnnouncedSecond: Long? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as WhisperTimeApplication).container
        timerEngine = container.timerEngine
        timingRecordRepository = container.timingRecordRepository
        voiceAnnouncementManager = container.voiceAnnouncementManager
        createNotificationChannel()
        observeTimerCompletion()
        observePrepareAnnouncements()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> stopForegroundService(stopAction = true)
            ACTION_CANCEL -> stopForegroundService(stopAction = false)
            null -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        completionJob?.cancel()
        prepareAnnouncementJob?.cancel()
        voiceAnnouncementManager.stopSpeaking()
        serviceScope.cancel()
        clearActiveSession(resetCompletionFlag = true)
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L).takeIf { it > 0L } ?: return
        currentProjectName = intent.getStringExtra(EXTRA_PROJECT_NAME).orEmpty()
        val timerMode = if (intent.getStringExtra(EXTRA_TIMER_MODE) == TimerMode.COUNTDOWN.name) {
            TimerMode.COUNTDOWN
        } else {
            TimerMode.COUNT_UP
        }
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, -1L).takeIf { it > 0L }
        val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, -1L).takeIf { it > 0L }
        val prepareMs = intent.getLongExtra(EXTRA_PREPARE_MS, -1L).takeIf { it > 0L }

        voiceAnnouncementManager.stopSpeaking()
        activeProjectId = projectId
        activeDurationMs = durationMs
        activeTimerMode = timerMode
        activeStartEpoch = System.currentTimeMillis()
        completionRecorded = false

        timerEngine.start(
            TimerConfig(
                projectId = projectId,
                projectName = currentProjectName,
                mode = timerMode,
                durationMs = durationMs,
                voiceIntervalMs = intervalMs,
                prepareTimeMs = prepareMs
            )
        )
        timerStatus = TimerStatus.RUNNING
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun handlePause() {
        if (timerStatus != TimerStatus.RUNNING) return
        timerEngine.pause()
        timerStatus = TimerStatus.PAUSED
        updateNotification()
    }

    private fun handleResume() {
        if (timerStatus != TimerStatus.PAUSED) return
        timerEngine.resume()
        timerStatus = TimerStatus.RUNNING
        updateNotification()
    }

    private fun stopForegroundService(stopAction: Boolean) {
        voiceAnnouncementManager.stopSpeaking()
        if (stopAction) {
            timerEngine.stop()
        } else {
            timerEngine.cancel()
        }
        clearActiveSession(resetCompletionFlag = true)
        timerStatus = TimerStatus.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observeTimerCompletion() {
        completionJob?.cancel()
        completionJob = serviceScope.launch {
            timerEngine.shouldAnnounce.collect { signal ->
                if (signal > 0L) {
                    if (activeTimerMode == TimerMode.COUNTDOWN) {
                        val remaining = (activeDurationMs ?: 0L) - signal
                        voiceAnnouncementManager.announceRemaining(remaining.coerceAtLeast(0L))
                    } else {
                        voiceAnnouncementManager.announceElapsed(signal)
                    }
                    return@collect
                }
                if (signal != -1L || completionRecorded) return@collect

                val projectId = activeProjectId ?: return@collect
                val durationMs = activeDurationMs ?: return@collect
                val endEpoch = System.currentTimeMillis()
                val startEpoch = if (activeStartEpoch > 0L) {
                    activeStartEpoch
                } else {
                    endEpoch - durationMs
                }

                timingRecordRepository.insert(
                    TimingRecordEntity(
                        projectId = projectId,
                        startTime = startEpoch,
                        endTime = endEpoch,
                        durationMs = durationMs,
                        createdAt = endEpoch
                    )
                )

                completionRecorded = true
                clearActiveSession(resetCompletionFlag = false)
                voiceAnnouncementManager.stopSpeaking()
                timerStatus = TimerStatus.IDLE
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun observePrepareAnnouncements() {
        prepareAnnouncementJob?.cancel()
        prepareAnnouncementJob = serviceScope.launch {
            timerEngine.prepareRemainingMs.collect { remainingMs ->
                val remainingSecond = remainingMs
                    ?.takeIf { it > 0L }
                    ?.let { (it + 999L) / 1000L }

                if (remainingSecond == null) {
                    lastPrepareAnnouncedSecond = null
                    return@collect
                }

                if (remainingSecond != lastPrepareAnnouncedSecond) {
                    voiceAnnouncementManager.announce(remainingSecond.toString())
                    lastPrepareAnnouncedSecond = remainingSecond
                }
            }
        }
    }

    private fun clearActiveSession(resetCompletionFlag: Boolean) {
        activeProjectId = null
        activeDurationMs = null
        activeTimerMode = TimerMode.COUNT_UP
        activeStartEpoch = 0L
        lastPrepareAnnouncedSecond = null
        if (resetCompletionFlag) {
            completionRecorded = false
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val contentTextRes = if (timerStatus == TimerStatus.PAUSED) {
            R.string.timer_service_notification_text_paused
        } else {
            R.string.timer_service_notification_text_running
        }
        val contentText = getString(contentTextRes).let { base ->
            if (currentProjectName.isBlank()) base else "$base: $currentProjectName"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.timer_service_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                0,
                getString(
                    if (timerStatus == TimerStatus.PAUSED) {
                        R.string.timer_service_action_resume
                    } else {
                        R.string.timer_service_action_pause
                    }
                ),
                actionPendingIntent(
                    if (timerStatus == TimerStatus.PAUSED) {
                        ACTION_RESUME
                    } else {
                        ACTION_PAUSE
                    }
                )
            )
            .addAction(
                0,
                getString(R.string.timer_service_action_stop),
                actionPendingIntent(ACTION_STOP)
            )
            .addAction(
                0,
                getString(R.string.timer_service_action_cancel),
                actionPendingIntent(ACTION_CANCEL)
            )
            .build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.timer_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.whispertime.action.START"
        const val ACTION_PAUSE = "com.example.whispertime.action.PAUSE"
        const val ACTION_RESUME = "com.example.whispertime.action.RESUME"
        const val ACTION_STOP = "com.example.whispertime.action.STOP"
        const val ACTION_CANCEL = "com.example.whispertime.action.CANCEL"
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_TIMER_MODE = "extra_timer_mode"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        const val EXTRA_PREPARE_MS = "extra_prepare_ms"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "timer_channel"
    }

    private enum class TimerStatus {
        IDLE,
        RUNNING,
        PAUSED
    }
}
