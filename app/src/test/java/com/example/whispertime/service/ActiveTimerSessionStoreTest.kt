package com.example.whispertime.service

import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 活跃计时会话存储测试，验证保存、读取和清理行为。 */
class ActiveTimerSessionStoreTest {

    /** 验证完整会话保存后可以原样读取。 */
    @Test
    fun saveAndLoad_roundTripsActiveSession() {
        val store = ActiveTimerSessionStore(FakeKeyValueStore())
        val session = ActiveTimerSession(
            projectId = 42L,
            projectName = "Focus",
            mode = TimerMode.COUNTDOWN,
            durationMs = 15_000L,
            voiceIntervalMs = 3_000L,
            vibrationEnabled = true,
            state = TimerState.RUNNING,
            prepareRemainingMs = null,
            prepareReferenceEpochMs = null,
            prepareReferenceElapsedRealtimeMs = null,
            sessionStartEpochMs = 123_000L,
            elapsedMs = 4_000L,
            runningReferenceElapsedRealtimeMs = 999L,
            lastAnnouncedElapsedMs = 3_000L
        )

        store.save(session)

        assertEquals(session, store.load())
    }

    /** 验证 clear 会移除已持久化的会话。 */
    @Test
    fun clear_removesPersistedSession() {
        val store = ActiveTimerSessionStore(FakeKeyValueStore())

        store.save(
            ActiveTimerSession(
                projectId = 1L,
                projectName = "Focus",
                mode = TimerMode.COUNT_UP,
                durationMs = null,
                voiceIntervalMs = 5_000L,
                vibrationEnabled = false,
                state = TimerState.PAUSED,
                prepareRemainingMs = null,
                prepareReferenceEpochMs = null,
                prepareReferenceElapsedRealtimeMs = null,
                sessionStartEpochMs = 8_000L,
                elapsedMs = 6_000L,
                runningReferenceElapsedRealtimeMs = null,
                lastAnnouncedElapsedMs = 5_000L
            )
        )

        store.clear()

        assertNull(store.load())
    }

    /** 测试用内存键值存储，替代 SharedPreferences。 */
    private class FakeKeyValueStore : KeyValueStore {
        /** 内存键值表。 */
        private val values = mutableMapOf<String, Any>()

        /** 读取字符串值。 */
        override fun getString(key: String): String? = values[key] as? String

        /** 读取 Long 值。 */
        override fun getLong(key: String): Long? = values[key] as? Long

        /** 读取 Boolean 值。 */
        override fun getBoolean(key: String): Boolean? = values[key] as? Boolean

        /** 写入或删除字符串值。 */
        override fun putString(key: String, value: String?) {
            putNullable(key, value)
        }

        /** 写入或删除 Long 值。 */
        override fun putLong(key: String, value: Long?) {
            putNullable(key, value)
        }

        /** 写入或删除 Boolean 值。 */
        override fun putBoolean(key: String, value: Boolean?) {
            putNullable(key, value)
        }

        /** 清空内存存储。 */
        override fun clear() {
            values.clear()
        }

        /** 统一处理 null 删除和非空写入。 */
        private fun putNullable(key: String, value: Any?) {
            if (value == null) {
                values.remove(key)
            } else {
                values[key] = value
            }
        }
    }
}
