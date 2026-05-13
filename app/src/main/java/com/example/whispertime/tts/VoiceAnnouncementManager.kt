package com.example.whispertime.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(tag, "audioFocusChange: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
            }
        }
    }

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
                            abandonAudioFocusIfNeeded()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String) {
                            Log.e(tag, "utterance onError id=$utteranceId")
                            abandonAudioFocusIfNeeded()
                        }

                        override fun onError(utteranceId: String, errorCode: Int) {
                            Log.e(tag, "utterance onError id=$utteranceId errorCode=$errorCode")
                            abandonAudioFocusIfNeeded()
                        }
                    }
                )

                // 设置AudioAttributes：使用MEDIA类型，确保耳机连接时声音只从耳机播放
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
                Log.d(tag, "onInit(): AudioAttributes set to USAGE_MEDIA")

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
        requestAudioFocus()
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
        requestAudioFocus()
        val utteranceId = "whispertime_${System.currentTimeMillis()}"
        Log.d(tag, "speak(QUEUE_ADD): id=$utteranceId text=$text")
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(tag, "requestAudioFocus(): result=$result hasFocus=$hasAudioFocus")
        } catch (e: Exception) {
            Log.e(tag, "requestAudioFocus(): failed", e)
        }
    }

    private fun abandonAudioFocusIfNeeded() {
        if (!hasAudioFocus) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
            hasAudioFocus = false
            Log.d(tag, "abandonAudioFocusIfNeeded(): abandoned")
        } catch (e: Exception) {
            Log.e(tag, "abandonAudioFocusIfNeeded(): failed", e)
        }
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
        abandonAudioFocusIfNeeded()
        Log.d(tag, "shutdown(): done")
    }

    fun stopSpeaking() {
        Log.d(tag, "stopSpeaking(): begin")
        pendingQueue.clear()
        tts?.stop()
        abandonAudioFocusIfNeeded()
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
