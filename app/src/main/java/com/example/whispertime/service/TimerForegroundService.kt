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
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
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

/** 前台计时服务，负责后台计时、通知栏控制、语音播报、震动和会话恢复。 */
class TimerForegroundService : Service() {

    /** 日志标签。 */
    private val tag = "TimerForegroundService"

    /** 全局计时引擎，和页面共享同一份计时状态。 */
    private lateinit var timerEngine: TimerEngine

    /** 计时记录仓库，用于停止计时时保存历史记录。 */
    private lateinit var timingRecordRepository: TimingRecordRepository

    /** 活跃会话持久化存储，用于服务或进程恢复。 */
    private lateinit var activeTimerSessionStore: ActiveTimerSessionStore

    /** 语音播报管理器。 */
    private lateinit var voiceManager: VoiceAnnouncementManager

    /** 震动提醒管理器。 */
    private lateinit var vibrationManager: VibrationManager

    /** 服务内协程作用域，跟随服务生命周期释放。 */
    private var serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 通知栏定时刷新任务。 */
    private var notificationJob: Job? = null

    /** 倒计时完成监听任务。 */
    private var completionJob: Job? = null

    /** 周期语音播报监听任务。 */
    private var announcementJob: Job? = null

    /** 预留的启动倒计时任务句柄。 */
    private var startCountdownJob: Job? = null

    /** 准备倒计时语音播报任务。 */
    private var preparingSpeechJob: Job? = null

    /** 当前项目名称，用于通知栏展示。 */
    private var currentProjectName: String = ""

    /** 当前计时模式，用于选择展示和播报内容。 */
    private var currentMode: TimerMode = TimerMode.COUNT_UP

    /** 当前计时配置快照。 */
    private var currentConfig: TimerConfig? = null

    /** 当前正式计时开始的墙钟时间。 */
    private var currentSessionStartEpochMs: Long? = null

    /** 最近一次语音播报对应的已计时毫秒数。 */
    private var lastAnnouncedElapsedMs: Long = 0L

    /** 当前计时是否开启震动提醒。 */
    private var vibrationEnabled: Boolean = false

    /** 是否正在处理倒计时自动完成，避免重复 stop。 */
    private var autoCompletionInProgress = false

    /** 后台计时使用的部分唤醒锁。 */
    private var wakeLock: PowerManager.WakeLock? = null

    /** 唤醒锁刷新任务，避免超时后后台计时中断。 */
    private var wakeLockRefreshJob: Job? = null

    /** 媒体会话，用于通知栏和耳机控制按钮。 */
    private var mediaSession: MediaSessionCompat? = null

    /** 初始化服务依赖、通知渠道、媒体会话，并尝试恢复上次会话。 */
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
        initMediaSession()
        restorePersistedSessionIfNeeded()
    }

    /** 处理来自页面、通知栏或系统重投递的服务命令。 */
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

    /** 该服务不支持绑定。 */
    override fun onBind(intent: Intent?): IBinder? = null

    /** 释放任务、唤醒锁、媒体会话和协程作用域。 */
    override fun onDestroy() {
        Log.d(tag, "onDestroy()")
        startCountdownJob?.cancel()
        preparingSpeechJob?.cancel()
        notificationJob?.cancel()
        completionJob?.cancel()
        announcementJob?.cancel()
        wakeLockRefreshJob?.cancel()
        vibrationManager.cancel()
        releaseWakeLock()
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 根据启动 intent 创建计时配置并启动前台服务计时。 */
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

        // 先启动引擎再根据状态持久化，避免页面和通知看到不一致状态。
        timerEngine.start(config)
        updateWakeLockForState(timerEngine.state.value)
        Log.d(tag, "handleStart(): engine started; state=${timerEngine.state.value}")

        val countdownSeconds = prepareTimeMs?.let { ((it + 999L) / 1000L).toInt() } ?: 0
        if (countdownSeconds <= 0) {
            // 没有准备倒计时时，立即记录正式开始时间并播报开始。
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
                    // prepareRemainingMs 为空表示准备阶段已结束，延迟一帧等待引擎切到 RUNNING。
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

                // 同一秒只播报一次，避免 100ms 状态刷新导致重复倒数。
                lastSpokenSecond = secondsLeft
                voiceManager.announceQueued(secondsLeft.toString())
                vibrateIfEnabled()
            }
        }
    }

    /** 停止计时，保存历史记录，并在必要时播报结束。 */
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
        // 只有存在活跃计时时才播报结束，空闲状态收到 stop 不打扰用户。
        val shouldAnnounceEnd = timerEngine.state.value != TimerState.IDLE
        if (shouldAnnounceEnd) {
            // 结束播报需要短暂保活，防止 stopSelf 过快导致 TTS 被系统打断。
            acquireWakeLock()
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
            if (shouldAnnounceEnd) {
                // 给最终语音播报留出完成时间，再释放唤醒锁和停止服务。
                delay(FINAL_ANNOUNCEMENT_GRACE_MS)
            }
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** 取消计时，不保存记录也不播报结束。 */
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

    /** 监听引擎完成信号，倒计时到零时自动停止并保存记录。 */
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

    /** 监听周期播报信号，根据当前模式播报已过时长或剩余时长。 */
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

    /** 如果当前计时开启震动，则触发一次提醒震动。 */
    private fun vibrateIfEnabled() {
        if (vibrationEnabled) {
            vibrationManager.vibrateReminder()
        }
    }

    /** 启动通知栏每秒刷新任务。 */
    private fun startNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(1000L)
            }
        }
    }

    /** 立即刷新前台通知内容。 */
    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** 构建计时前台通知，包含当前时间展示和暂停/继续/停止操作。 */
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
        updateMediaSessionPlaybackState(state)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentProjectName)
            .setContentText(displayTime)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)

        if (state == TimerState.RUNNING || state == TimerState.PREPARING) {
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

        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0)
            )
        }

        return builder.build()
    }

    /** 创建通知栏按钮对应的服务 PendingIntent。 */
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

    /** 创建低优先级通知渠道，用于前台服务计时通知。 */
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

    /** 初始化媒体会话，让通知栏媒体控制和耳机按钮能暂停、继续、停止计时。 */
    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "WhisperTimer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                /** 媒体暂停按钮映射到计时暂停。 */
                override fun onPause() {
                    if (timerEngine.state.value == TimerState.RUNNING) {
                        timerEngine.pause()
                        persistPausedSession()
                        updateWakeLockForState(timerEngine.state.value)
                        updateNotification()
                    }
                }

                /** 媒体播放按钮映射到计时继续。 */
                override fun onPlay() {
                    if (timerEngine.state.value == TimerState.PAUSED) {
                        timerEngine.resume()
                        persistRunningSession()
                        updateWakeLockForState(timerEngine.state.value)
                        startNotificationUpdates()
                    }
                }

                /** 媒体停止按钮映射到计时停止。 */
                override fun onStop() {
                    handleStop()
                }
            })
            isActive = true
        }
    }

    /** 根据计时状态同步媒体会话播放状态和可用动作。 */
    private fun updateMediaSessionPlaybackState(state: TimerState) {
        val session = mediaSession ?: return
        val playbackState = when (state) {
            TimerState.RUNNING, TimerState.PREPARING -> PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
                .build()
            TimerState.PAUSED -> PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
                .build()
            else -> PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                .setActions(0L)
                .build()
        }
        session.setPlaybackState(playbackState)
    }

    /** 服务重建时从本地持久化快照恢复活跃计时。 */
    private fun restorePersistedSessionIfNeeded() {
        if (timerEngine.state.value != TimerState.IDLE) return
        val session = activeTimerSessionStore.load() ?: return

        runCatching {
            // 使用单调时钟补偿服务未运行期间的流逝时间。
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
                // 恢复后的状态要重新落盘，刷新参考时间防止后续再次恢复时重复补偿。
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

    /** 持久化准备阶段状态，保存准备倒计时和双时钟参考点。 */
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

    /** 持久化运行阶段状态，保存正式开始时间、已过时长和运行参考时钟。 */
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

    /** 持久化暂停阶段状态，暂停期间不需要运行参考时钟。 */
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

    /** 将恢复器计算出的状态重新保存为当前参考点。 */
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

    /** 根据计时状态获取或释放唤醒锁。 */
    private fun updateWakeLockForState(state: TimerState) {
        if (state == TimerState.PREPARING || state == TimerState.RUNNING) {
            acquireWakeLock()
            startWakeLockRefresh()
        } else {
            wakeLockRefreshJob?.cancel()
            wakeLockRefreshJob = null
            releaseWakeLock()
        }
    }

    /** 获取 10 分钟部分唤醒锁，配合刷新任务长期保活后台计时。 */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = wakeLock ?: powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:timer"
        ).apply {
            setReferenceCounted(false)
        }
        if (wakeLock?.isHeld == true) return
        wakeLock?.acquire(10 * 60 * 1000L)
        Log.d(tag, "acquireWakeLock(): acquired with 10min timeout")
    }

    /** 定期刷新唤醒锁，避免系统在长计时期间释放 CPU。 */
    private fun startWakeLockRefresh() {
        if (wakeLockRefreshJob?.isActive == true) return
        wakeLockRefreshJob = serviceScope.launch {
            delay(9 * 60 * 1000L)
            while (isActive) {
                val state = timerEngine.state.value
                if (state == TimerState.PREPARING || state == TimerState.RUNNING) {
                    releaseWakeLockInternal()
                    // 释放后立即重新获取，以刷新超时时间。
                    acquireWakeLock()
                } else {
                    cancel()
                    return@launch
                }
                delay(9 * 60 * 1000L)
            }
        }
    }

    /** 停止刷新任务并释放唤醒锁。 */
    private fun releaseWakeLock() {
        wakeLockRefreshJob?.cancel()
        wakeLockRefreshJob = null
        releaseWakeLockInternal()
    }

    /** 只释放唤醒锁本身，不影响刷新任务状态。 */
    private fun releaseWakeLockInternal() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
        }
    }

    /** 将毫秒数格式化为通知栏展示的 mm:ss 或 hh:mm:ss。 */
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

    /** 服务 action、intent extra、通知和保活常量。 */
    companion object {
        /** 启动计时 action。 */
        const val ACTION_START = "com.example.whispertime.action.START"
        /** 暂停计时 action。 */
        const val ACTION_PAUSE = "com.example.whispertime.action.PAUSE"
        /** 继续计时 action。 */
        const val ACTION_RESUME = "com.example.whispertime.action.RESUME"
        /** 停止计时并保存记录 action。 */
        const val ACTION_STOP = "com.example.whispertime.action.STOP"
        /** 取消计时且不保存记录 action。 */
        const val ACTION_CANCEL = "com.example.whispertime.action.CANCEL"
        /** 项目主键 extra。 */
        const val EXTRA_PROJECT_ID = "extra_project_id"
        /** 项目名称 extra。 */
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        /** 计时模式 extra。 */
        const val EXTRA_TIMER_MODE = "extra_timer_mode"
        /** 倒计时总时长 extra。 */
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        /** 语音播报间隔 extra。 */
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        /** 震动开关 extra。 */
        const val EXTRA_VIBRATION_ENABLED = "extra_vibration_enabled"
        /** 准备倒计时时长 extra。 */
        const val EXTRA_PREPARE_MS = "extra_prepare_ms"
        /** 前台服务通知 ID。 */
        const val NOTIFICATION_ID = 1001
        /** 前台服务通知渠道 ID。 */
        const val CHANNEL_ID = "timer_channel"
        /** 最终结束播报的保活宽限时间。 */
        private const val FINAL_ANNOUNCEMENT_GRACE_MS = 2_000L
    }
}
