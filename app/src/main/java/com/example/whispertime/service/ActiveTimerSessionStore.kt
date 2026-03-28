package com.example.whispertime.service

import android.content.Context
import android.content.SharedPreferences
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState

interface KeyValueStore {
    fun getString(key: String): String?
    fun getLong(key: String): Long?
    fun getBoolean(key: String): Boolean?
    fun putString(key: String, value: String?)
    fun putLong(key: String, value: Long?)
    fun putBoolean(key: String, value: Boolean?)
    fun clear()
}

class SharedPreferencesKeyValueStore(
    private val preferences: SharedPreferences
) : KeyValueStore {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun getLong(key: String): Long? {
        return if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    }

    override fun getBoolean(key: String): Boolean? {
        return if (preferences.contains(key)) preferences.getBoolean(key, false) else null
    }

    override fun putString(key: String, value: String?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    override fun putLong(key: String, value: Long?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putLong(key, value)
        }.apply()
    }

    override fun putBoolean(key: String, value: Boolean?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putBoolean(key, value)
        }.apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }
}

class ActiveTimerSessionStore(
    private val store: KeyValueStore
) {
    fun save(session: ActiveTimerSession) {
        store.putLong(KEY_PROJECT_ID, session.projectId)
        store.putString(KEY_PROJECT_NAME, session.projectName)
        store.putString(KEY_MODE, session.mode.name)
        store.putLong(KEY_DURATION_MS, session.durationMs)
        store.putLong(KEY_VOICE_INTERVAL_MS, session.voiceIntervalMs)
        store.putBoolean(KEY_VIBRATION_ENABLED, session.vibrationEnabled)
        store.putString(KEY_STATE, session.state.name)
        store.putLong(KEY_PREPARE_REMAINING_MS, session.prepareRemainingMs)
        store.putLong(KEY_PREPARE_REFERENCE_EPOCH_MS, session.prepareReferenceEpochMs)
        store.putLong(KEY_PREPARE_REFERENCE_ELAPSED_MS, session.prepareReferenceElapsedRealtimeMs)
        store.putLong(KEY_SESSION_START_EPOCH_MS, session.sessionStartEpochMs)
        store.putLong(KEY_ELAPSED_MS, session.elapsedMs)
        store.putLong(KEY_RUNNING_REFERENCE_ELAPSED_MS, session.runningReferenceElapsedRealtimeMs)
        store.putLong(KEY_LAST_ANNOUNCED_ELAPSED_MS, session.lastAnnouncedElapsedMs)
    }

    fun load(): ActiveTimerSession? {
        val projectId = store.getLong(KEY_PROJECT_ID) ?: return null
        val projectName = store.getString(KEY_PROJECT_NAME) ?: return null
        val mode = store.getString(KEY_MODE)?.let(TimerMode::valueOf) ?: return null
        val state = store.getString(KEY_STATE)?.let(TimerState::valueOf) ?: return null

        return ActiveTimerSession(
            projectId = projectId,
            projectName = projectName,
            mode = mode,
            durationMs = store.getLong(KEY_DURATION_MS),
            voiceIntervalMs = store.getLong(KEY_VOICE_INTERVAL_MS),
            vibrationEnabled = store.getBoolean(KEY_VIBRATION_ENABLED) ?: false,
            state = state,
            prepareRemainingMs = store.getLong(KEY_PREPARE_REMAINING_MS),
            prepareReferenceEpochMs = store.getLong(KEY_PREPARE_REFERENCE_EPOCH_MS),
            prepareReferenceElapsedRealtimeMs = store.getLong(KEY_PREPARE_REFERENCE_ELAPSED_MS),
            sessionStartEpochMs = store.getLong(KEY_SESSION_START_EPOCH_MS),
            elapsedMs = store.getLong(KEY_ELAPSED_MS) ?: 0L,
            runningReferenceElapsedRealtimeMs = store.getLong(KEY_RUNNING_REFERENCE_ELAPSED_MS),
            lastAnnouncedElapsedMs = store.getLong(KEY_LAST_ANNOUNCED_ELAPSED_MS) ?: 0L
        )
    }

    fun clear() {
        store.clear()
    }

    companion object {
        private const val PREFS_NAME = "active_timer_session"
        private const val KEY_PROJECT_ID = "project_id"
        private const val KEY_PROJECT_NAME = "project_name"
        private const val KEY_MODE = "mode"
        private const val KEY_DURATION_MS = "duration_ms"
        private const val KEY_VOICE_INTERVAL_MS = "voice_interval_ms"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_STATE = "state"
        private const val KEY_PREPARE_REMAINING_MS = "prepare_remaining_ms"
        private const val KEY_PREPARE_REFERENCE_EPOCH_MS = "prepare_reference_epoch_ms"
        private const val KEY_PREPARE_REFERENCE_ELAPSED_MS = "prepare_reference_elapsed_ms"
        private const val KEY_SESSION_START_EPOCH_MS = "session_start_epoch_ms"
        private const val KEY_ELAPSED_MS = "elapsed_ms"
        private const val KEY_RUNNING_REFERENCE_ELAPSED_MS = "running_reference_elapsed_ms"
        private const val KEY_LAST_ANNOUNCED_ELAPSED_MS = "last_announced_elapsed_ms"

        fun fromContext(context: Context): ActiveTimerSessionStore {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ActiveTimerSessionStore(SharedPreferencesKeyValueStore(prefs))
        }
    }
}
