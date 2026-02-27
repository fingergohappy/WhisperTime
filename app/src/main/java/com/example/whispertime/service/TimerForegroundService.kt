package com.example.whispertime.service

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
    private var foregroundNotification: Notification? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        foregroundNotification = buildNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundWithMinimumNotification()
            ACTION_STOP,
            ACTION_CANCEL -> stopForegroundService()
            ACTION_PAUSE,
            ACTION_RESUME,
            null -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun startForegroundWithMinimumNotification() {
        val notification = foregroundNotification ?: buildNotification().also {
            foregroundNotification = it
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.timer_service_notification_title))
            .setContentText(getString(R.string.timer_service_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()
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
}
