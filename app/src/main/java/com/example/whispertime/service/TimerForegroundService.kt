package com.example.whispertime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.whispertime.WhisperTimeApplication
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.timer.TimerConfig
import com.example.whispertime.timer.TimerEngine
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import com.example.whispertime.tts.VoiceAnnouncementManager
import com.example.whispertime.vibration.VibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerForegroundService : Service() {

    private val tag = "TimerForegroundService"

    private lateinit var timerEngine: TimerEngine
    private lateinit var timingRecordRepository: TimingRecordRepository
    private lateinit var voiceManager: VoiceAnnouncementManager
    private lateinit var vibrationManager: VibrationManager
    private var serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notificationJob: Job? = null
    private var completionJob: Job? = null
    private var announcementJob: Job? = null
    private var startCountdownJob: Job? = null
    private var preparingSpeechJob: Job? = null
    private var currentProjectName: String = ""
    private var currentMode: TimerMode = TimerMode.COUNT_UP
    private var vibrationEnabled: Boolean = false
    private var autoCompletionInProgress = false

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "onCreate()")
        val container = (application as WhisperTimeApplication).container
        timerEngine = container.timerEngine
        timingRecordRepository = container.timingRecordRepository
        voiceManager = container.voiceAnnouncementManager
        vibrationManager = container.vibrationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "onStartCommand(): action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> {
                Log.d(tag, "ACTION_PAUSE")
                timerEngine.pause()
                updateNotification()
            }
            ACTION_RESUME -> {
                Log.d(tag, "ACTION_RESUME")
                timerEngine.resume()
                startNotificationUpdates()
            }
            ACTION_STOP -> handleStop()
            ACTION_CANCEL -> handleCancel()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(tag, "onDestroy()")
        startCountdownJob?.cancel()
        preparingSpeechJob?.cancel()
        notificationJob?.cancel()
        completionJob?.cancel()
        announcementJob?.cancel()
        vibrationManager.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        startCountdownJob?.cancel()
        preparingSpeechJob?.cancel()
        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
        val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: ""
        val modeName = intent.getStringExtra(EXTRA_TIMER_MODE) ?: TimerMode.COUNT_UP.name
        val mode = if (modeName == TimerMode.COUNTDOWN.name) TimerMode.COUNTDOWN else TimerMode.COUNT_UP
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L).takeIf { it > 0L }
        val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, 0L).takeIf { it > 0L }
        vibrationEnabled = intent.getBooleanExtra(EXTRA_VIBRATION_ENABLED, false)
        val prepareTimeMs = intent.getLongExtra(EXTRA_PREPARE_MS, 0L).takeIf { it > 0L }

        Log.d(
            tag,
            "handleStart(): projectId=$projectId name=$projectName mode=$mode durationMs=$durationMs intervalMs=$intervalMs prepareTimeMs=$prepareTimeMs"
        )

        currentProjectName = projectName
        currentMode = mode

        val config = TimerConfig(
            projectId = projectId,
            projectName = projectName,
            mode = mode,
            durationMs = durationMs,
            voiceIntervalMs = intervalMs,
            vibrationEnabled = vibrationEnabled,
            prepareTimeMs = prepareTimeMs
        )

        startForeground(NOTIFICATION_ID, buildNotification())
        startNotificationUpdates()
        observeCompletion()
        observeAnnouncements()

        timerEngine.start(config)
        Log.d(tag, "handleStart(): engine started; state=${timerEngine.state.value}")

        val countdownSeconds = prepareTimeMs?.let { ((it + 999L) / 1000L).toInt() } ?: 0
        if (countdownSeconds <= 0) {
            voiceManager.announceQueued("开始")
            vibrateIfEnabled()
            return
        }

        preparingSpeechJob = serviceScope.launch {
            var lastSpokenSecond = Int.MIN_VALUE
            var hasSpokenStart = false

            timerEngine.prepareRemainingMs.collect { remainingMs ->
                if (!isActive) return@collect
                if (remainingMs == null) {
                    if (!hasSpokenStart) {
                        delay(50L)
                        if (timerEngine.state.value == TimerState.RUNNING) {
                            hasSpokenStart = true
                            voiceManager.announceQueued("开始")
                            vibrateIfEnabled()
                        }
                    }
                    cancel()
                    return@collect
                }
                val secondsLeft = ((remainingMs + 999L) / 1000L).toInt()
                if (secondsLeft <= 0) return@collect
                if (secondsLeft == lastSpokenSecond) return@collect

                lastSpokenSecond = secondsLeft
                voiceManager.announceQueued(secondsLeft.toString())
                vibrateIfEnabled()
            }
        }
    }

    private fun handleStop() {
        Log.d(tag, "handleStop(): state=${timerEngine.state.value}")
        startCountdownJob?.cancel()
        preparingSpeechJob?.cancel()
        voiceManager.stopSpeaking()
        vibrationManager.cancel()
        notificationJob?.cancel()
        completionJob?.cancel()
        announcementJob?.cancel()
        if (timerEngine.state.value != TimerState.IDLE) {
            voiceManager.announceEnd()
            vibrateIfEnabled()
        }
        serviceScope.launch {
            val result = timerEngine.stop()
            Log.d(tag, "handleStop(): engine stop result=$result")
            if (result != null) {
                val record = TimingRecordEntity(
                    projectId = result.projectId,
                    startTime = result.startTimeEpoch,
                    endTime = result.endTimeEpoch,
                    durationMs = result.durationMs,
                    createdAt = System.currentTimeMillis()
                )
                timingRecordRepository.insert(record)
            }
            autoCompletionInProgress = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleCancel() {
        Log.d(tag, "handleCancel(): state=${timerEngine.state.value}")
        startCountdownJob?.cancel()
        preparingSpeechJob?.cancel()
        voiceManager.stopSpeaking()
        vibrationManager.cancel()
        notificationJob?.cancel()
        completionJob?.cancel()
        announcementJob?.cancel()
        timerEngine.cancel()
        autoCompletionInProgress = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observeCompletion() {
        completionJob?.cancel()
        completionJob = serviceScope.launch {
            timerEngine.shouldAnnounce.collect { signal ->
                if (signal == -1L) {
                    Log.d(tag, "observeCompletion(): completion signal received")
                }
                if (signal == -1L && !autoCompletionInProgress) {
                    autoCompletionInProgress = true
                    handleStop()
                }
            }
        }
    }

    private fun observeAnnouncements() {
        announcementJob?.cancel()
        announcementJob = serviceScope.launch {
            timerEngine.shouldAnnounce.collect { signal ->
                if (signal == -1L) return@collect
                val state = timerEngine.state.value
                Log.d(tag, "observeAnnouncements(): signal=$signal state=$state mode=$currentMode")
                if (state == TimerState.RUNNING) {
                    if (currentMode == TimerMode.COUNTDOWN) {
                        voiceManager.announceRemaining(timerEngine.remainingMs.value ?: 0L)
                    } else {
                        voiceManager.announceElapsed(timerEngine.elapsedMs.value)
                    }
                    vibrateIfEnabled()
                }
            }
        }
    }

    private fun vibrateIfEnabled() {
        if (vibrationEnabled) {
            vibrationManager.vibrateReminder()
        }
    }

    private fun startNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(1000L)
            }
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val displayTime = when (currentMode) {
            TimerMode.COUNTDOWN -> formatTime(timerEngine.remainingMs.value ?: 0L)
            TimerMode.COUNT_UP -> formatTime(timerEngine.elapsedMs.value)
        }

        val state = timerEngine.state.value
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentProjectName)
            .setContentText(displayTime)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)

        if (state == TimerState.RUNNING) {
            builder.addAction(
                0,
                "暂停",
                buildActionPendingIntent(ACTION_PAUSE, 0)
            )
        } else if (state == TimerState.PAUSED) {
            builder.addAction(
                0,
                "继续",
                buildActionPendingIntent(ACTION_RESUME, 1)
            )
        }

        builder.addAction(
            0,
            "停止",
            buildActionPendingIntent(ACTION_STOP, 2)
        )

        return builder.build()
    }

    private fun buildActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "计时器",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
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
        const val EXTRA_VIBRATION_ENABLED = "extra_vibration_enabled"
        const val EXTRA_PREPARE_MS = "extra_prepare_ms"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "timer_channel"
    }
}
