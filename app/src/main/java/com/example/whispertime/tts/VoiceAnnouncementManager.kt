package com.example.whispertime.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** 语音播报管理器，封装 TTS 初始化、队列、音频焦点和播报格式化。 */
class VoiceAnnouncementManager(private val context: Context) {

    /** 日志标签。 */
    private val tag = "VoiceAnnouncement"

    /** Android TTS 实例，初始化失败或关闭后为空。 */
    private var tts: TextToSpeech? = null

    /** 内部 TTS 就绪状态。 */
    private val _isReady = MutableStateFlow(false)

    /** 对外暴露的 TTS 就绪状态。 */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** 保护待播队列和播报 ID 集合的同步锁。 */
    private val lock = Any()

    /** TTS 未就绪时暂存的播报文本队列。 */
    private val pendingQueue = ArrayDeque<String>()

    /** 已提交给 TTS 但尚未结束回调的 utteranceId 集合。 */
    private val pendingUtteranceIds = mutableSetOf<String>()

    /** 当前是否正在初始化 TTS。 */
    private var isInitializing = false

    /** 最近一次初始化尝试的单调时间。 */
    private var lastInitAttemptElapsedMs = 0L

    /** 系统音频管理器，用于申请临时音频焦点。 */
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /** Android O 及以上使用的音频焦点请求对象。 */
    private var audioFocusRequest: AudioFocusRequest? = null

    /** 当前是否已经持有音频焦点。 */
    private var hasAudioFocus = false

    /** 音频焦点变化监听器，维护当前焦点状态。 */
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

    /** 初始化 TTS，并清空旧的待播队列。 */
    fun init() {
        restartTts(clearPendingQueue = true)
    }

    /** 重启 TTS 引擎，可选择是否保留待播队列。 */
    private fun restartTts(clearPendingQueue: Boolean) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            // 初始化失败回调可能短时间内连续触发，最小间隔保护避免重启风暴。
            if (isInitializing && now - lastInitAttemptElapsedMs < INIT_RETRY_MIN_INTERVAL_MS) {
                return
            }
            isInitializing = true
            lastInitAttemptElapsedMs = now
            pendingUtteranceIds.clear()
            if (clearPendingQueue) {
                pendingQueue.clear()
            }
        }
        Log.d(tag, "restartTts(): begin; clearPendingQueue=$clearPendingQueue wasReady=${_isReady.value}")
        tts?.shutdown()
        tts = null
        _isReady.value = false

        tts = TextToSpeech(context) { status ->
            Log.d(tag, "onInit(): status=$status")
            synchronized(lock) {
                isInitializing = false
            }
            if (status == TextToSpeech.SUCCESS) {
                // 读取默认引擎只用于诊断，异常不影响播报流程。
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
                        /** 单条播报开始回调。 */
                        override fun onStart(utteranceId: String) {
                            Log.d(tag, "utterance onStart id=$utteranceId")
                        }

                        /** 单条播报完成回调，尝试释放音频焦点。 */
                        override fun onDone(utteranceId: String) {
                            Log.d(tag, "utterance onDone id=$utteranceId")
                            markUtteranceFinished(utteranceId)
                        }

                        /** 旧版错误回调，兼容低版本 TTS。 */
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String) {
                            Log.e(tag, "utterance onError id=$utteranceId")
                            markUtteranceFinished(utteranceId)
                        }

                        /** 新版错误回调，失败后保留队列并重启 TTS。 */
                        override fun onError(utteranceId: String, errorCode: Int) {
                            Log.e(tag, "utterance onError id=$utteranceId errorCode=$errorCode")
                            markUtteranceFinished(utteranceId)
                            restartTts(clearPendingQueue = false)
                        }

                        /** 播报被停止时清理对应 utterance 状态。 */
                        override fun onStop(utteranceId: String, interrupted: Boolean) {
                            Log.d(tag, "utterance onStop id=$utteranceId interrupted=$interrupted")
                            markUtteranceFinished(utteranceId)
                        }
                    }
                )

                // 导航引导语音类型更适合短播报，并可和耳机/蓝牙路由协作。
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
                Log.d(tag, "onInit(): AudioAttributes set to USAGE_ASSISTANCE_NAVIGATION_GUIDANCE")

                _isReady.value = ready
                if (ready) {
                    // TTS 就绪后按原顺序补播初始化期间积压的文本。
                    drainPendingQueue()
                } else {
                    Log.w(tag, "onInit(): ready=false; announcements will be queued only")
                }
            } else {
                Log.e(tag, "onInit(): FAILED status=$status; announcements will be queued only")
            }
        }
    }

    /** 立即播报文本，并清空 TTS 内部未播完内容。 */
    fun announce(text: String) {
        Log.d(tag, "announce(): ready=${_isReady.value} queueSize=${pendingQueue.size} text=$text")
        if (!_isReady.value) {
            enqueuePending(text)
            return
        }
        speakNow(text, TextToSpeech.QUEUE_FLUSH)
    }

    /** 追加播报文本，不打断已经排队的语音。 */
    fun announceQueued(text: String) {
        Log.d(tag, "announceQueued(): ready=${_isReady.value} queueSize=${pendingQueue.size} text=$text")
        if (!_isReady.value) {
            enqueuePending(text)
            return
        }
        speakNow(text, TextToSpeech.QUEUE_ADD)
    }

    /** 入队待播文本，必要时触发 TTS 重启。 */
    private fun enqueuePending(text: String, addFirst: Boolean = false) {
        synchronized(lock) {
            if (addFirst) {
                pendingQueue.addFirst(text)
            } else {
                pendingQueue.addLast(text)
            }
        }
        if (!_isReady.value && !isInitializing) {
            // TTS 未就绪且没有初始化中的任务时，主动尝试恢复引擎。
            restartTts(clearPendingQueue = false)
        }
    }

    /** 按 FIFO 顺序清空待播队列。 */
    private fun drainPendingQueue() {
        Log.d(tag, "drainPendingQueue(): ready=true; queueSize=${pendingQueue.size}")
        while (true) {
            val text = synchronized(lock) {
                pendingQueue.removeFirstOrNull()
            } ?: return
            if (!speakNow(text, TextToSpeech.QUEUE_ADD)) {
                return
            }
        }
    }

    /** 向 TTS 提交一次播报，失败时回队列头并重启引擎。 */
    private fun speakNow(text: String, queueMode: Int): Boolean {
        requestAudioFocus()
        val utteranceId = "whispertime_${System.currentTimeMillis()}"
        synchronized(lock) {
            if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                // QUEUE_FLUSH 会停止旧播报，因此旧 utterance 不再等待完成回调。
                pendingUtteranceIds.clear()
            }
            pendingUtteranceIds.add(utteranceId)
        }
        val result = tts?.speak(text, queueMode, null, utteranceId) ?: TextToSpeech.ERROR
        val queueName = if (queueMode == TextToSpeech.QUEUE_FLUSH) "QUEUE_FLUSH" else "QUEUE_ADD"
        Log.d(tag, "speak($queueName): id=$utteranceId result=$result text=$text")
        if (result == TextToSpeech.SUCCESS) {
            return true
        }

        // 提交失败时不要丢播报内容，放回队列头等待 TTS 恢复。
        markUtteranceFinished(utteranceId)
        enqueuePending(text, addFirst = true)
        restartTts(clearPendingQueue = false)
        return false
    }

    /** 标记播报完成，并在全部播报结束后释放音频焦点。 */
    private fun markUtteranceFinished(utteranceId: String) {
        val shouldAbandonFocus = synchronized(lock) {
            pendingUtteranceIds.remove(utteranceId)
            pendingUtteranceIds.isEmpty()
        }
        if (shouldAbandonFocus) {
            abandonAudioFocusIfNeeded()
        }
    }

    /** 申请短暂可降音的音频焦点，避免播报被背景音完全盖住。 */
    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
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

    /** 已无待播内容时释放音频焦点。 */
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

    /** 播报已计时时长。 */
    fun announceElapsed(elapsedMs: Long) {
        announce(formatIntervalText(elapsedMs))
    }

    /** 播报倒计时剩余时长。 */
    fun announceRemaining(remainingMs: Long) {
        announce(formatIntervalText(remainingMs))
    }

    /** 播报计时开始提示。 */
    fun announceStart() {
        announce("计时开始")
    }

    /** 播报计时结束提示。 */
    fun announceEnd() {
        announce("计时结束")
    }

    /** 关闭 TTS 并清理队列、焦点和状态。 */
    fun shutdown() {
        Log.d(tag, "shutdown(): begin")
        synchronized(lock) {
            pendingQueue.clear()
            pendingUtteranceIds.clear()
            isInitializing = false
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        abandonAudioFocusIfNeeded()
        Log.d(tag, "shutdown(): done")
    }

    /** 停止当前播报并清空待播队列。 */
    fun stopSpeaking() {
        Log.d(tag, "stopSpeaking(): begin")
        synchronized(lock) {
            pendingQueue.clear()
            pendingUtteranceIds.clear()
        }
        tts?.stop()
        abandonAudioFocusIfNeeded()
        Log.d(tag, "stopSpeaking(): done")
    }

    /** 将毫秒数转换为适合中文 TTS 的短文本。 */
    private fun formatIntervalText(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        if (totalSeconds >= 60 && totalSeconds % 60 == 0L) {
            val minutes = totalSeconds / 60
            return "${minutes}分钟"
        }
        return totalSeconds.toString()
    }

    /** TTS 初始化节流常量。 */
    private companion object {
        /** 两次初始化尝试之间的最短间隔。 */
        const val INIT_RETRY_MIN_INTERVAL_MS = 1_000L
    }
}
