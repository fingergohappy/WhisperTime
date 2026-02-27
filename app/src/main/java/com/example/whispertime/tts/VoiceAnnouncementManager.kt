package com.example.whispertime.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class VoiceAnnouncementManager(private val context: Context) {

    enum class ReadinessState {
        NOT_INITIALIZED,
        INITIALIZING,
        READY,
        FAILED
    }

    private val tag = "VoiceAnnouncement"
    private val lock = Any()

    private var tts: TextToSpeech? = null
    private var isSpeaking = false
    private var isReleased = false
    private var currentUtteranceId: String? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    private val _readinessState = MutableStateFlow(ReadinessState.NOT_INITIALIZED)
    val readinessState: StateFlow<ReadinessState> = _readinessState.asStateFlow()
    private val pendingQueue = ArrayDeque<String>()
    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            Log.d(tag, "utterance onStart id=$utteranceId")
        }

        override fun onDone(utteranceId: String) {
            onUtteranceFinished(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            Log.e(tag, "utterance onError id=$utteranceId")
            onUtteranceFinished(utteranceId)
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            Log.e(tag, "utterance onError id=$utteranceId errorCode=$errorCode")
            onUtteranceFinished(utteranceId)
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            Log.d(tag, "utterance onStop id=$utteranceId interrupted=$interrupted")
            onUtteranceFinished(utteranceId)
        }
    }

    fun init() {
        Log.d(tag, "init(): begin; wasReady=${_isReady.value}")
        val oldTts = synchronized(lock) {
            val previous = tts
            tts = null
            isSpeaking = false
            currentUtteranceId = null
            _isReady.value = false
            _readinessState.value = ReadinessState.INITIALIZING
            pendingQueue.clear()
            isReleased = false
            previous
        }
        oldTts?.shutdown()

        tts = TextToSpeech(context) { status ->
            Log.d(tag, "onInit(): status=$status")
            if (status == TextToSpeech.SUCCESS) {
                val localTts = tts
                if (localTts == null) {
                    Log.w(tag, "onInit(): instance released before success callback")
                    synchronized(lock) {
                        _isReady.value = false
                        _readinessState.value = ReadinessState.FAILED
                        pendingQueue.clear()
                    }
                    return@TextToSpeech
                }
                val engine = runCatching { localTts.defaultEngine }
                    .getOrElse { "<error:${it.javaClass.simpleName}>" }
                Log.d(tag, "onInit(): SUCCESS; defaultEngine=$engine")

                val result = localTts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                val ready = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED
                Log.d(
                    tag,
                    "onInit(): setLanguage zh_CN result=$result (MISSING=${TextToSpeech.LANG_MISSING_DATA}, NOT_SUPPORTED=${TextToSpeech.LANG_NOT_SUPPORTED}); ready=$ready"
                )

                localTts.setOnUtteranceProgressListener(utteranceListener)

                synchronized(lock) {
                    _isReady.value = ready
                    _readinessState.value = if (ready) {
                        ReadinessState.READY
                    } else {
                        ReadinessState.FAILED
                    }
                }

                if (ready) {
                    drainQueueIfPossible()
                } else {
                    synchronized(lock) {
                        pendingQueue.clear()
                    }
                    Log.w(tag, "onInit(): ready=false; dropping announcements")
                }
            } else {
                Log.e(tag, "onInit(): FAILED status=$status; announcements will be queued only")
                synchronized(lock) {
                    _isReady.value = false
                    _readinessState.value = ReadinessState.FAILED
                    pendingQueue.clear()
                    isSpeaking = false
                    currentUtteranceId = null
                }
            }
        }
    }

    fun announce(text: String) {
        enqueueAnnouncement(text)
    }

    fun announceQueued(text: String) {
        enqueueAnnouncement(text)
    }

    fun announceElapsed(elapsedMs: Long) {
        announce(formatIntervalText(elapsedMs))
    }

    fun announceRemaining(remainingMs: Long) {
        announce(formatIntervalText(remainingMs))
    }

    fun announceStart() {
        announce("计时开始")
    }

    fun announceEnd() {
        announce("计时结束")
    }

    fun shutdown() {
        release()
    }

    fun stopSpeaking() {
        Log.d(tag, "stopSpeaking(): begin")
        val localTts = synchronized(lock) {
            pendingQueue.clear()
            isSpeaking = false
            currentUtteranceId = null
            tts
        }
        localTts?.stop()
        Log.d(tag, "stopSpeaking(): done")
    }

    fun release() {
        Log.d(tag, "release(): begin")
        val localTts = synchronized(lock) {
            isReleased = true
            pendingQueue.clear()
            isSpeaking = false
            currentUtteranceId = null
            _isReady.value = false
            _readinessState.value = ReadinessState.NOT_INITIALIZED
            val current = tts
            tts = null
            current
        }
        localTts?.stop()
        localTts?.shutdown()
        Log.d(tag, "release(): done")
    }

    private fun formatIntervalText(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        if (totalSeconds >= 60 && totalSeconds % 60 == 0L) {
            val minutes = totalSeconds / 60
            return "${minutes}分钟"
        }
        return totalSeconds.toString()
    }

    private fun enqueueAnnouncement(text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) {
            return
        }
        val accepted = synchronized(lock) {
            if (isReleased) {
                Log.w(tag, "enqueueAnnouncement(): ignored because manager already released")
                return@synchronized false
            }
            if (_readinessState.value == ReadinessState.FAILED) {
                Log.w(tag, "enqueueAnnouncement(): ignored because tts state is FAILED")
                return@synchronized false
            }
            pendingQueue.addLast(normalizedText)
            true
        }
        if (!accepted) {
            return
        }
        drainQueueIfPossible()
    }

    private fun onUtteranceFinished(utteranceId: String) {
        synchronized(lock) {
            if (currentUtteranceId == utteranceId) {
                isSpeaking = false
                currentUtteranceId = null
            }
        }
        drainQueueIfPossible()
    }

    private fun drainQueueIfPossible() {
        while (true) {
            val nextToSpeak = synchronized(lock) {
                if (_readinessState.value != ReadinessState.READY || isSpeaking || isReleased) {
                    return
                }
                val next = pendingQueue.removeFirstOrNull() ?: return
                val localTts = tts
                if (localTts == null) {
                    isSpeaking = false
                    currentUtteranceId = null
                    return
                }
                val utteranceId = "whispertime_${UUID.randomUUID()}"
                isSpeaking = true
                currentUtteranceId = utteranceId
                Triple(localTts, next, utteranceId)
            }

            val result = nextToSpeak.first.speak(
                nextToSpeak.second,
                TextToSpeech.QUEUE_ADD,
                null,
                nextToSpeak.third
            )
            if (result != TextToSpeech.SUCCESS) {
                Log.w(tag, "drainQueueIfPossible(): speak failed result=$result")
                synchronized(lock) {
                    isSpeaking = false
                    currentUtteranceId = null
                }
                continue
            }
            Log.d(tag, "speak(QUEUE_ADD): id=${nextToSpeak.third} text=${nextToSpeak.second}")
            return
        }
    }
}
