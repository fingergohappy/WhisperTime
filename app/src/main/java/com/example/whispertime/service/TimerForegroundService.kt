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

class TimerForegroundService : Service() {
    private var timerStatus: TimerStatus = TimerStatus.IDLE
    private var currentProjectName: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP,
            ACTION_CANCEL -> stopForegroundService()
            null -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        currentProjectName = intent.getStringExtra(EXTRA_PROJECT_NAME).orEmpty()
        timerStatus = TimerStatus.RUNNING
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun handlePause() {
        if (timerStatus != TimerStatus.RUNNING) return
        timerStatus = TimerStatus.PAUSED
        updateNotification()
    }

    private fun handleResume() {
        if (timerStatus != TimerStatus.PAUSED) return
        timerStatus = TimerStatus.RUNNING
        updateNotification()
    }

    private fun stopForegroundService() {
        timerStatus = TimerStatus.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
