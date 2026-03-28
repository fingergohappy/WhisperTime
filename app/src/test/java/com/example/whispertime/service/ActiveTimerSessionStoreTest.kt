package com.example.whispertime.service

import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveTimerSessionStoreTest {

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

    private class FakeKeyValueStore : KeyValueStore {
        private val values = mutableMapOf<String, Any>()

        override fun getString(key: String): String? = values[key] as? String

        override fun getLong(key: String): Long? = values[key] as? Long

        override fun getBoolean(key: String): Boolean? = values[key] as? Boolean

        override fun putString(key: String, value: String?) {
            putNullable(key, value)
        }

        override fun putLong(key: String, value: Long?) {
            putNullable(key, value)
        }

        override fun putBoolean(key: String, value: Boolean?) {
            putNullable(key, value)
        }

        override fun clear() {
            values.clear()
        }

        private fun putNullable(key: String, value: Any?) {
            if (value == null) {
                values.remove(key)
            } else {
                values[key] = value
            }
        }
    }
}
