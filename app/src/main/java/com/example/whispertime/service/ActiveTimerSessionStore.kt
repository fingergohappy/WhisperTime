package com.example.whispertime.service

import android.content.Context
import android.content.SharedPreferences
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState

/** 键值存储抽象，便于单元测试替换 SharedPreferences。 */
interface KeyValueStore {
    /** 读取字符串值，缺失时返回 null。 */
    fun getString(key: String): String?

    /** 读取 Long 值，缺失时返回 null。 */
    fun getLong(key: String): Long?

    /** 读取 Boolean 值，缺失时返回 null。 */
    fun getBoolean(key: String): Boolean?

    /** 写入字符串值，传 null 时删除该键。 */
    fun putString(key: String, value: String?)

    /** 写入 Long 值，传 null 时删除该键。 */
    fun putLong(key: String, value: Long?)

    /** 写入 Boolean 值，传 null 时删除该键。 */
    fun putBoolean(key: String, value: Boolean?)

    /** 清空全部会话数据。 */
    fun clear()
}

/** 基于 SharedPreferences 的键值存储实现。 */
class SharedPreferencesKeyValueStore(
    /** Android 持久化键值对象。 */
    private val preferences: SharedPreferences
) : KeyValueStore {
    /** 读取字符串值。 */
    override fun getString(key: String): String? = preferences.getString(key, null)

    /** 读取 Long 值，先检查 key 是否存在以区分缺失和 0。 */
    override fun getLong(key: String): Long? {
        return if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    }

    /** 读取 Boolean 值，先检查 key 是否存在以区分缺失和 false。 */
    override fun getBoolean(key: String): Boolean? {
        return if (preferences.contains(key)) preferences.getBoolean(key, false) else null
    }

    /** 写入或删除字符串值。 */
    override fun putString(key: String, value: String?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    /** 写入或删除 Long 值。 */
    override fun putLong(key: String, value: Long?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putLong(key, value)
        }.apply()
    }

    /** 写入或删除 Boolean 值。 */
    override fun putBoolean(key: String, value: Boolean?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putBoolean(key, value)
        }.apply()
    }

    /** 清空 SharedPreferences 中的会话数据。 */
    override fun clear() {
        preferences.edit().clear().apply()
    }
}

/** 活跃计时会话存取器，负责把复杂会话拆分为键值并恢复。 */
class ActiveTimerSessionStore(
    /** 实际键值存储实现。 */
    private val store: KeyValueStore
) {
    /** 保存当前计时会话快照。 */
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

    /** 从键值存储恢复会话，核心字段缺失时认为没有可恢复会话。 */
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

    /** 清除持久化的活跃会话。 */
    fun clear() {
        store.clear()
    }

    /** SharedPreferences 名称、字段 key 以及工厂方法。 */
    companion object {
        /** 保存活跃会话的 SharedPreferences 文件名。 */
        private const val PREFS_NAME = "active_timer_session"
        /** 项目主键字段。 */
        private const val KEY_PROJECT_ID = "project_id"
        /** 项目名称字段。 */
        private const val KEY_PROJECT_NAME = "project_name"
        /** 计时模式字段。 */
        private const val KEY_MODE = "mode"
        /** 倒计时总时长字段。 */
        private const val KEY_DURATION_MS = "duration_ms"
        /** 语音播报间隔字段。 */
        private const val KEY_VOICE_INTERVAL_MS = "voice_interval_ms"
        /** 震动开关字段。 */
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        /** 保存时状态字段。 */
        private const val KEY_STATE = "state"
        /** 准备倒计时剩余字段。 */
        private const val KEY_PREPARE_REMAINING_MS = "prepare_remaining_ms"
        /** 准备阶段墙钟参考时间字段。 */
        private const val KEY_PREPARE_REFERENCE_EPOCH_MS = "prepare_reference_epoch_ms"
        /** 准备阶段单调时钟参考时间字段。 */
        private const val KEY_PREPARE_REFERENCE_ELAPSED_MS = "prepare_reference_elapsed_ms"
        /** 正式计时开始墙钟字段。 */
        private const val KEY_SESSION_START_EPOCH_MS = "session_start_epoch_ms"
        /** 已计时时长字段。 */
        private const val KEY_ELAPSED_MS = "elapsed_ms"
        /** 运行阶段单调时钟参考时间字段。 */
        private const val KEY_RUNNING_REFERENCE_ELAPSED_MS = "running_reference_elapsed_ms"
        /** 最近播报时长字段。 */
        private const val KEY_LAST_ANNOUNCED_ELAPSED_MS = "last_announced_elapsed_ms"

        /** 从 Android Context 创建默认的会话存取器。 */
        fun fromContext(context: Context): ActiveTimerSessionStore {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ActiveTimerSessionStore(SharedPreferencesKeyValueStore(prefs))
        }
    }
}
