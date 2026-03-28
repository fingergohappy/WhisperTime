package com.example.whispertime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.whispertime.WhisperTimeApplication
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.timer.ActiveTimerSessionResolver
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
    private lateinit var activeTimerSessionStore: ActiveTimerSessionStore
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
    private var currentConfig: TimerConfig? = null
    private var currentSessionStartEpochMs: Long? = null
    private var lastAnnouncedElapsedMs: Long = 0L
    private var vibrationEnabled: Boolean = false
    private var autoCompletionInProgress = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "onCreate()")
        val container = (application as WhisperTimeApplication).container
        timerEngine = container.timerEngine
        timingRecordRepository = container.timingRecordRepository
        activeTimerSessionStore = container.activeTimerSessionStore
        voiceManager = container.voiceAnnouncementManager
        vibrationManager = container.vibrationManager
        createNotificationChannel()
        restorePersistedSessionIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "onStartCommand(): action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> {
                Log.d(tag, "ACTION_PAUSE")
                timerEngine.pause()
                persistPausedSession()
                updateWakeLockForState(timerEngine.state.value)
                updateNotification()
            }
            ACTION_RESUME -> {
                Log.d(tag, "ACTION_RESUME")
                timerEngine.resume()
                persistRunningSession()
                updateWakeLockForState(timerEngine.state.value)
                startNotificationUpdates()
            }
            ACTION_STOP -> handleStop()
            ACTION_CANCEL -> handleCancel()
            null -> restorePersistedSessionIfNeeded()
        }
        return START_REDELIVER_INTENT
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
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        if (timerEngine.state.value != TimerState.IDLE) {
            Log.d(tag, "handleStart(): ignored because timer state=${timerEngine.state.value}")
            return
        }
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
        lastAnnouncedElapsedMs = 0L
        currentSessionStartEpochMs = null

        val config = TimerConfig(
            projectId = projectId,
            projectName = projectName,
            mode = mode,
            durationMs = durationMs,
            voiceIntervalMs = intervalMs,
            vibrationEnabled = vibrationEnabled,
            prepareTimeMs = prepareTimeMs
        )
        currentConfig = config

        startForeground(NOTIFICATION_ID, buildNotification())
        startNotificationUpdates()
        observeCompletion()
        observeAnnouncements()

        timerEngine.start(config)
        updateWakeLockForState(timerEngine.state.value)
        Log.d(tag, "handleStart(): engine started; state=${timerEngine.state.value}")

        val countdownSeconds = prepareTimeMs?.let { ((it + 999L) / 1000L).toInt() } ?: 0
        if (countdownSeconds <= 0) {
            currentSessionStartEpochMs = System.currentTimeMillis()
            persistRunningSession()
            voiceManager.announceQueued("开始")
            vibrateIfEnabled()
            return
        }

        persistPreparingSession(requireNotNull(prepareTimeMs))
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
                            currentSessionStartEpochMs = System.currentTimeMillis()
                            persistRunningSession()
                            updateWakeLockForState(timerEngine.state.value)
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
        activeTimerSessionStore.clear()
        releaseWakeLock()
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
            currentConfig = null
            currentSessionStartEpochMs = null
            lastAnnouncedElapsedMs = 0L
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
        activeTimerSessionStore.clear()
        releaseWakeLock()
        timerEngine.cancel()
        autoCompletionInProgress = false
        currentConfig = null
        currentSessionStartEpochMs = null
        lastAnnouncedElapsedMs = 0L
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
                    lastAnnouncedElapsedMs = signal
                    persistRunningSession()
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
        val state = timerEngine.state.value
        val displayTime = if (state == TimerState.PREPARING) {
            formatTime(timerEngine.prepareRemainingMs.value ?: 0L)
        } else {
            when (currentMode) {
                TimerMode.COUNTDOWN -> formatTime(timerEngine.remainingMs.value ?: 0L)
                TimerMode.COUNT_UP -> formatTime(timerEngine.elapsedMs.value)
            }
        }
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

    private fun restorePersistedSessionIfNeeded() {
        if (timerEngine.state.value != TimerState.IDLE) return
        val session = activeTimerSessionStore.load() ?: return

        runCatching {
            val resolved = ActiveTimerSessionResolver.resolve(
                session = session,
                nowElapsedRealtimeMs = SystemClock.elapsedRealtime()
            )
            currentConfig = resolved.config
            currentProjectName = session.projectName
            currentMode = session.mode
            vibrationEnabled = session.vibrationEnabled
            currentSessionStartEpochMs = resolved.sessionStartEpochMs
            lastAnnouncedElapsedMs = resolved.lastAnnouncedElapsedMs

            observeCompletion()
            observeAnnouncements()
            timerEngine.restore(resolved)
            if (!resolved.shouldComplete) {
                persistResolvedSession(resolved)
            }
            startForeground(NOTIFICATION_ID, buildNotification())
            startNotificationUpdates()
            updateWakeLockForState(timerEngine.state.value)
            Log.d(tag, "restorePersistedSessionIfNeeded(): restored state=${timerEngine.state.value}")
        }.onFailure { throwable ->
            Log.e(tag, "restorePersistedSessionIfNeeded(): failed", throwable)
            activeTimerSessionStore.clear()
            currentConfig = null
            currentSessionStartEpochMs = null
            lastAnnouncedElapsedMs = 0L
            releaseWakeLock()
        }
    }

    private fun persistPreparingSession(remainingMs: Long) {
        val config = currentConfig ?: return
        activeTimerSessionStore.save(
            ActiveTimerSession(
                projectId = config.projectId,
                projectName = config.projectName,
                mode = config.mode,
                durationMs = config.durationMs,
                voiceIntervalMs = config.voiceIntervalMs,
                vibrationEnabled = config.vibrationEnabled,
                state = TimerState.PREPARING,
                prepareRemainingMs = remainingMs,
                prepareReferenceEpochMs = System.currentTimeMillis(),
                prepareReferenceElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                sessionStartEpochMs = null,
                elapsedMs = 0L,
                runningReferenceElapsedRealtimeMs = null,
                lastAnnouncedElapsedMs = lastAnnouncedElapsedMs
            )
        )
    }

    private fun persistRunningSession() {
        val config = currentConfig ?: return
        val sessionStartEpochMs = currentSessionStartEpochMs ?: System.currentTimeMillis().also {
            currentSessionStartEpochMs = it
        }
        activeTimerSessionStore.save(
            ActiveTimerSession(
                projectId = config.projectId,
                projectName = config.projectName,
                mode = config.mode,
                durationMs = config.durationMs,
                voiceIntervalMs = config.voiceIntervalMs,
                vibrationEnabled = config.vibrationEnabled,
                state = TimerState.RUNNING,
                prepareRemainingMs = null,
                prepareReferenceEpochMs = null,
                prepareReferenceElapsedRealtimeMs = null,
                sessionStartEpochMs = sessionStartEpochMs,
                elapsedMs = timerEngine.elapsedMs.value,
                runningReferenceElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                lastAnnouncedElapsedMs = lastAnnouncedElapsedMs
            )
        )
    }

    private fun persistPausedSession() {
        val config = currentConfig ?: return
        activeTimerSessionStore.save(
            ActiveTimerSession(
                projectId = config.projectId,
                projectName = config.projectName,
                mode = config.mode,
                durationMs = config.durationMs,
                voiceIntervalMs = config.voiceIntervalMs,
                vibrationEnabled = config.vibrationEnabled,
                state = TimerState.PAUSED,
                prepareRemainingMs = null,
                prepareReferenceEpochMs = null,
                prepareReferenceElapsedRealtimeMs = null,
                sessionStartEpochMs = currentSessionStartEpochMs,
                elapsedMs = timerEngine.elapsedMs.value,
                runningReferenceElapsedRealtimeMs = null,
                lastAnnouncedElapsedMs = lastAnnouncedElapsedMs
            )
        )
    }

    private fun persistResolvedSession(resolved: com.example.whispertime.timer.ResolvedActiveTimerSession) {
        val config = resolved.config
        when (resolved.state) {
            TimerState.PREPARING -> {
                activeTimerSessionStore.save(
                    ActiveTimerSession(
                        projectId = config.projectId,
                        projectName = config.projectName,
                        mode = config.mode,
                        durationMs = config.durationMs,
                        voiceIntervalMs = config.voiceIntervalMs,
                        vibrationEnabled = config.vibrationEnabled,
                        state = TimerState.PREPARING,
                        prepareRemainingMs = resolved.prepareRemainingMs,
                        prepareReferenceEpochMs = System.currentTimeMillis(),
                        prepareReferenceElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        sessionStartEpochMs = null,
                        elapsedMs = 0L,
                        runningReferenceElapsedRealtimeMs = null,
                        lastAnnouncedElapsedMs = resolved.lastAnnouncedElapsedMs
                    )
                )
            }

            TimerState.RUNNING -> persistRunningSession()
            TimerState.PAUSED -> persistPausedSession()
            TimerState.IDLE -> activeTimerSessionStore.clear()
        }
    }

    private fun updateWakeLockForState(state: TimerState) {
        if (state == TimerState.PREPARING || state == TimerState.RUNNING) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = wakeLock ?: powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:timer"
        ).apply {
            setReferenceCounted(false)
        }
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
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
