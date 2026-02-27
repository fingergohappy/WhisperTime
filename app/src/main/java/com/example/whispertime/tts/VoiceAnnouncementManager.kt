package com.example.whispertime.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceAnnouncementManager(private val context: Context) {

    private val tag = "VoiceAnnouncement"

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    private val pendingQueue = ArrayDeque<String>()

    fun init() {
        Log.d(tag, "init(): begin; wasReady=${_isReady.value}")
        tts?.shutdown()
        tts = null
        _isReady.value = false
        pendingQueue.clear()

        tts = TextToSpeech(context) { status ->
            Log.d(tag, "onInit(): status=$status")
            if (status == TextToSpeech.SUCCESS) {
                val engine = try {
                    tts?.defaultEngine
                } catch (t: Throwable) {
                    "<error:${t.javaClass.simpleName}>"
                }
                Log.d(tag, "onInit(): SUCCESS; defaultEngine=$engine")

                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                val ready = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED
                Log.d(
                    tag,
                    "onInit(): setLanguage zh_CN result=$result (MISSING=${TextToSpeech.LANG_MISSING_DATA}, NOT_SUPPORTED=${TextToSpeech.LANG_NOT_SUPPORTED}); ready=$ready"
                )

                tts?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            Log.d(tag, "utterance onStart id=$utteranceId")
                        }

                        override fun onDone(utteranceId: String) {
                            Log.d(tag, "utterance onDone id=$utteranceId")
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String) {
                            Log.e(tag, "utterance onError id=$utteranceId")
                        }

                        override fun onError(utteranceId: String, errorCode: Int) {
                            Log.e(tag, "utterance onError id=$utteranceId errorCode=$errorCode")
                        }
                    }
                )

                _isReady.value = ready
                if (ready) {
                    Log.d(tag, "onInit(): ready=true; draining pendingQueue size=${pendingQueue.size}")
                    while (pendingQueue.isNotEmpty()) {
                        val text = pendingQueue.removeFirst()
                        val utteranceId = "whispertime_${System.currentTimeMillis()}"
                        Log.d(tag, "speak(QUEUE_ADD): id=$utteranceId text=$text")
                        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
                    }
                } else {
                    Log.w(tag, "onInit(): ready=false; announcements will be queued only")
                }
            } else {
                Log.e(tag, "onInit(): FAILED status=$status; announcements will be queued only")
            }
        }
    }

    fun announce(text: String) {
        Log.d(tag, "announce(): ready=${_isReady.value} queueSize=${pendingQueue.size} text=$text")
        if (!_isReady.value) {
            pendingQueue.addLast(text)
            return
        }
        val utteranceId = "whispertime_${System.currentTimeMillis()}"
        Log.d(tag, "speak(QUEUE_FLUSH): id=$utteranceId text=$text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun announceQueued(text: String) {
        Log.d(tag, "announceQueued(): ready=${_isReady.value} queueSize=${pendingQueue.size} text=$text")
        if (!_isReady.value) {
            pendingQueue.addLast(text)
            return
        }
        val utteranceId = "whispertime_${System.currentTimeMillis()}"
        Log.d(tag, "speak(QUEUE_ADD): id=$utteranceId text=$text")
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
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
        Log.d(tag, "shutdown(): begin")
        pendingQueue.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        Log.d(tag, "shutdown(): done")
    }

    fun stopSpeaking() {
        Log.d(tag, "stopSpeaking(): begin")
        pendingQueue.clear()
        tts?.stop()
        Log.d(tag, "stopSpeaking(): done")
    }

    private fun formatIntervalText(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        if (totalSeconds >= 60 && totalSeconds % 60 == 0L) {
            val minutes = totalSeconds / 60
            return "${minutes}分钟"
        }
        return totalSeconds.toString()
    }
}
