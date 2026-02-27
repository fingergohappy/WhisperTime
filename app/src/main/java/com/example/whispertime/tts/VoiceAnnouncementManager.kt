package com.example.whispertime.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 语音播报管理器，负责管理 TextToSpeech (TTS) 的生命周期、状态控制和队列语义。
 *
 * 核心机制：
 * 1. 初始化异步性：TTS 引擎初始化需要时间，通过 [isReady] 流暴露就绪状态。
 * 2. 缓冲队列：在 TTS 未就绪时，播报请求会被存入 [pendingQueue]，待初始化完成后按序“排空”（drain）。
 * 3. 播报策略：[announce] 使用 QUEUE_FLUSH（立即打断上一个），[announceQueued] 使用 QUEUE_ADD（追加到末尾）。
 *
 * @param context 建议传入 ApplicationContext 以避免 Activity 泄漏。
 */
class VoiceAnnouncementManager(private val context: Context) {

    private val tag = "VoiceAnnouncement"

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)

    /**
     * 对外暴露的 TTS 就绪状态流。
     */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /**
     * 初始化期间的播报请求缓冲。
     */
    private val pendingQueue = ArrayDeque<String>()

    /**
     * 执行初始化或重新初始化。
     * 释放旧资源，重置状态，并启动新的 TTS 实例。
     */
    fun init() {
        Log.d(tag, "init(): begin; wasReady=${_isReady.value}")
        tts?.shutdown()
        tts = null
        _isReady.value = false
        pendingQueue.clear()

        tts = TextToSpeech(context) { status ->
            Log.d(tag, "onInit(): status=$status")
            if (status == TextToSpeech.SUCCESS) {
                // 检查引擎可用性
                val engine = try {
                    tts?.defaultEngine
                } catch (t: Throwable) {
                    "<error:${t.javaClass.simpleName}>"
                }
                Log.d(tag, "onInit(): SUCCESS; defaultEngine=$engine")

                // 设置语言为简体中文
                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                val ready = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED
                Log.d(
                    tag,
                    "onInit(): setLanguage zh_CN result=$result (MISSING=${TextToSpeech.LANG_MISSING_DATA}, NOT_SUPPORTED=${TextToSpeech.LANG_NOT_SUPPORTED}); ready=$ready"
                )

                // 注册进度监听，用于调试播报生命周期
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
                    // 初始化成功，立即处理积压的播报请求
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

    /**
     * 立即播报文本（打断当前正在播放的内容）。
     * 若未就绪，则存入队列。
     */
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

    /**
     * 将文本加入播报队列（不打断当前内容）。
     * 适用于倒计时秒数等连续播报场景。
     */
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

    /**
     * 释放 TTS 引擎资源，清空队列并重置状态。
     * 应该在不再需要播报（如应用关闭）时调用。
     */
    fun shutdown() {
        Log.d(tag, "shutdown(): begin")
        pendingQueue.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        Log.d(tag, "shutdown(): done")
    }

    /**
     * 停止当前说话并清空待处理队列，但不销毁引擎。
     */
    fun stopSpeaking() {
        Log.d(tag, "stopSpeaking(): begin")
        pendingQueue.clear()
        tts?.stop()
        Log.d(tag, "stopSpeaking(): done")
    }

    /**
     * 播报已过去的时长。
     */
    fun announceElapsed(elapsedMs: Long) {
        announce(formatIntervalText(elapsedMs))
    }

    /**
     * 播报剩余时长。
     */
    fun announceRemaining(remainingMs: Long) {
        announce(formatIntervalText(remainingMs))
    }

    /**
     * 播报“计时开始”。
     */
    fun announceStart() {
        announce("计时开始")
    }

    /**
     * 播报“计时结束”。
     */
    fun announceEnd() {
        announce("计时结束")
    }

    /**
     * 将时长毫秒数格式化为适合播报的中文文本。
     * 例如：60000ms -> "1分钟"，5000ms -> "5"。
     */
    private fun formatIntervalText(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        if (totalSeconds >= 60 && totalSeconds % 60 == 0L) {
            val minutes = totalSeconds / 60
            return "${minutes}分钟"
        }
        return totalSeconds.toString()
    }
}
