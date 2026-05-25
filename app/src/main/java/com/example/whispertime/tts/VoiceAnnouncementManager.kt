package com.example.whispertime.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/** 语音播报管理器，封装 TTS 文件合成、应用内播放、队列、音频焦点和播报格式化。 */
class VoiceAnnouncementManager(private val context: Context) {

    /** 单条播报请求，记录 TTS 合成文件、队列世代和重试次数。 */
    private data class SpeechRequest(
        /** TTS 合成请求 ID，用于匹配异步回调。 */
        val id: String,
        /** 待播报文本。 */
        val text: String,
        /** TTS 合成输出文件。 */
        val file: File,
        /** 队列世代，用于忽略 flush 或 stop 之后到达的旧回调。 */
        val generation: Long,
        /** 合成失败后的重试次数。 */
        val retryCount: Int = 0
    )

    /** 日志标签。 */
    private val tag = "VoiceAnnouncement"

    /** Android TTS 实例，只负责把文本合成为缓存音频文件。 */
    private var tts: TextToSpeech? = null

    /** 内部 TTS 就绪状态。 */
    private val _isReady = MutableStateFlow(false)

    /** 对外暴露的 TTS 就绪状态。 */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** 保护待播队列、当前请求和播放器状态的同步锁。 */
    private val lock = Any()

    /** TTS 未就绪或正在播放时暂存的播报请求队列。 */
    private val pendingQueue = ArrayDeque<SpeechRequest>()

    /** 当前正在合成或正在播放的播报请求。 */
    private var currentRequest: SpeechRequest? = null

    /** 当前请求是否正在等待 TTS 文件合成完成。 */
    private var isSynthesizing = false

    /** 当前由本应用持有的短语音播放器。 */
    private var mediaPlayer: MediaPlayer? = null

    /** 主线程 Handler，用于串行创建和启动 MediaPlayer。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 队列世代号，flush、stop、shutdown 时递增以废弃旧回调。 */
    private var playbackGeneration = 0L

    /** 递增请求序号，用于生成稳定且唯一的 utteranceId。 */
    private var requestCounter = 0L

    /** TTS 合成缓存目录，位于应用私有缓存中。 */
    private val synthesisDir: File by lazy {
        File(context.cacheDir, "voice_announcements")
    }

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

    /** 初始化 TTS，并清空旧的待播队列和遗留合成文件。 */
    fun init() {
        cleanSynthesisCache()
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
            if (clearPendingQueue) {
                playbackGeneration += 1L
                clearQueuedFilesLocked()
                currentRequest?.file?.delete()
                currentRequest = null
                isSynthesizing = false
            }
        }
        if (clearPendingQueue) {
            stopAndReleasePlayer()
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
                        /** 单条语音文件合成开始回调。 */
                        override fun onStart(utteranceId: String) {
                            Log.d(tag, "synthesis onStart id=$utteranceId")
                        }

                        /** 单条语音文件合成完成回调，随后交给本应用播放器发声。 */
                        override fun onDone(utteranceId: String) {
                            Log.d(tag, "synthesis onDone id=$utteranceId")
                            handleSynthesisDone(utteranceId)
                        }

                        /** 旧版错误回调，兼容低版本 TTS。 */
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String) {
                            Log.e(tag, "synthesis onError id=$utteranceId")
                            handleSynthesisFailure(
                                utteranceId = utteranceId,
                                restartEngine = true,
                                keepText = true
                            )
                        }

                        /** 新版错误回调，失败后保留一次重试机会并重启 TTS。 */
                        override fun onError(utteranceId: String, errorCode: Int) {
                            Log.e(tag, "synthesis onError id=$utteranceId errorCode=$errorCode")
                            handleSynthesisFailure(
                                utteranceId = utteranceId,
                                restartEngine = true,
                                keepText = true
                            )
                        }

                        /** 合成被停止时清理对应请求，不再补播旧内容。 */
                        override fun onStop(utteranceId: String, interrupted: Boolean) {
                            Log.d(tag, "synthesis onStop id=$utteranceId interrupted=$interrupted")
                            handleSynthesisFailure(
                                utteranceId = utteranceId,
                                restartEngine = false,
                                keepText = false
                            )
                        }
                    }
                )

                // TTS 只合成文件；真实播放由本应用 MediaPlayer 完成，避免系统 TTS 后台播放被静音。
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
                Log.d(tag, "onInit(): AudioAttributes set for synthesis")

                _isReady.value = ready
                if (ready) {
                    processNextSpeech()
                } else {
                    Log.w(tag, "onInit(): ready=false; announcements will be queued only")
                }
            } else {
                Log.e(tag, "onInit(): FAILED status=$status; announcements will be queued only")
            }
        }
    }

    /** 立即播报文本，并清空 TTS 和本应用播放器里的旧内容。 */
    fun announce(text: String) {
        val queueSize = synchronized(lock) { pendingQueue.size }
        Log.d(tag, "announce(): ready=${_isReady.value} queueSize=$queueSize text=$text")
        enqueueAnnouncement(text = text, flushExisting = true)
    }

    /** 追加播报文本，不打断已经排队或正在播放的语音。 */
    fun announceQueued(text: String) {
        val queueSize = synchronized(lock) { pendingQueue.size }
        Log.d(tag, "announceQueued(): ready=${_isReady.value} queueSize=$queueSize text=$text")
        enqueueAnnouncement(text = text, flushExisting = false)
    }

    /** 将播报文本转成请求入队，必要时触发 TTS 初始化或继续处理队列。 */
    private fun enqueueAnnouncement(text: String, flushExisting: Boolean) {
        if (text.isBlank()) return
        if (flushExisting) {
            synchronized(lock) {
                playbackGeneration += 1L
                clearQueuedFilesLocked()
                currentRequest?.file?.delete()
                currentRequest = null
                isSynthesizing = false
            }
            tts?.stop()
            stopAndReleasePlayer()
        }

        var shouldRestart = false
        var shouldProcess = false
        synchronized(lock) {
            pendingQueue.addLast(createSpeechRequestLocked(text))
            shouldRestart = !_isReady.value && !isInitializing
            shouldProcess = _isReady.value && currentRequest == null && !isSynthesizing
        }

        if (shouldRestart) {
            restartTts(clearPendingQueue = false)
        }
        if (shouldProcess) {
            processNextSpeech()
        }
    }

    /** 创建一条新的播报请求，调用方必须持有 lock。 */
    private fun createSpeechRequestLocked(text: String): SpeechRequest {
        val id = nextUtteranceIdLocked()
        return SpeechRequest(
            id = id,
            text = text,
            file = File(synthesisDir, "$id.wav"),
            generation = playbackGeneration
        )
    }

    /** 生成唯一合成请求 ID，调用方必须持有 lock。 */
    private fun nextUtteranceIdLocked(): String {
        requestCounter += 1L
        return "whispertime_${System.currentTimeMillis()}_$requestCounter"
    }

    /** 如果空闲且 TTS 已就绪，则取出下一条请求并开始合成文件。 */
    private fun processNextSpeech() {
        val request = synchronized(lock) {
            if (!_isReady.value || isSynthesizing || currentRequest != null) return
            val next = pendingQueue.removeFirstOrNull() ?: return
            currentRequest = next
            isSynthesizing = true
            next
        }

        ensureSynthesisDir()
        val result = tts?.synthesizeToFile(
            request.text,
            Bundle(),
            request.file,
            request.id
        ) ?: TextToSpeech.ERROR
        Log.d(
            tag,
            "synthesizeToFile(): id=${request.id} result=$result file=${request.file.name} text=${request.text}"
        )
        if (result != TextToSpeech.SUCCESS) {
            handleSynthesisFailure(
                utteranceId = request.id,
                restartEngine = true,
                keepText = true
            )
        }
    }

    /** 处理 TTS 文件合成完成，过滤过期回调后切到主线程播放。 */
    private fun handleSynthesisDone(utteranceId: String) {
        val request = synchronized(lock) {
            val current = currentRequest ?: return
            if (current.id != utteranceId) return
            isSynthesizing = false
            current
        }
        mainHandler.post {
            playSynthesizedFile(request)
        }
    }

    /** 处理 TTS 合成失败或停止，并按需要保留一次重试。 */
    private fun handleSynthesisFailure(
        utteranceId: String,
        restartEngine: Boolean,
        keepText: Boolean
    ) {
        var shouldRestart = false
        var shouldProcess = false
        synchronized(lock) {
            val failed = currentRequest ?: return
            if (failed.id != utteranceId) return
            currentRequest = null
            isSynthesizing = false
            failed.file.delete()
            if (keepText &&
                failed.retryCount < MAX_SYNTHESIS_RETRY_COUNT &&
                failed.generation == playbackGeneration
            ) {
                val retryId = nextUtteranceIdLocked()
                pendingQueue.addFirst(
                    failed.copy(
                        id = retryId,
                        file = File(synthesisDir, "$retryId.wav"),
                        retryCount = failed.retryCount + 1
                    )
                )
            }
            shouldRestart = restartEngine
            shouldProcess = !restartEngine
        }
        if (shouldRestart) {
            restartTts(clearPendingQueue = false)
        }
        if (shouldProcess) {
            processNextSpeech()
        }
    }

    /** 使用本应用 MediaPlayer 播放已经合成好的语音文件。 */
    private fun playSynthesizedFile(request: SpeechRequest) {
        val shouldPlay = synchronized(lock) {
            val current = currentRequest
            current?.id == request.id && current.generation == request.generation
        }
        if (!shouldPlay) {
            request.file.delete()
            return
        }
        if (!request.file.exists() || request.file.length() <= 0L) {
            Log.e(tag, "playSynthesizedFile(): missing or empty file id=${request.id}")
            finishCurrentRequest(request, player = null)
            return
        }

        var player: MediaPlayer? = null
        try {
            requestAudioFocus()
            player = MediaPlayer().apply {
                // 使用媒体用法让声音归属本应用的前台媒体服务，避开系统 TTS 引擎后台播放静音。
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(request.file.absolutePath)
                setOnCompletionListener { completedPlayer ->
                    Log.d(tag, "media playback onCompletion id=${request.id}")
                    finishCurrentRequest(request, completedPlayer)
                }
                setOnErrorListener { errorPlayer, what, extra ->
                    Log.e(tag, "media playback onError id=${request.id} what=$what extra=$extra")
                    finishCurrentRequest(request, errorPlayer)
                    true
                }
                prepare()
            }

            val shouldStart = synchronized(lock) {
                val current = currentRequest
                if (current?.id == request.id && current.generation == request.generation) {
                    mediaPlayer = player
                    true
                } else {
                    false
                }
            }
            if (!shouldStart) {
                player.release()
                request.file.delete()
                abandonAudioFocusIfNeeded()
                return
            }

            player.start()
            Log.d(tag, "media playback start id=${request.id} text=${request.text}")
        } catch (e: Exception) {
            Log.e(tag, "playSynthesizedFile(): failed id=${request.id}", e)
            synchronized(lock) {
                if (mediaPlayer === player) {
                    mediaPlayer = null
                }
            }
            runCatching { player?.release() }
            finishCurrentRequest(request, player = null)
        }
    }

    /** 完成当前播报请求，释放播放器、删除缓存文件并继续处理下一条。 */
    private fun finishCurrentRequest(request: SpeechRequest, player: MediaPlayer?) {
        var shouldReleasePlayer = false
        var shouldContinue = false
        synchronized(lock) {
            if (mediaPlayer === player) {
                mediaPlayer = null
                shouldReleasePlayer = true
            }
            val current = currentRequest
            if (current?.id == request.id) {
                currentRequest = null
                isSynthesizing = false
                request.file.delete()
                shouldContinue = true
            }
        }
        if (shouldReleasePlayer) {
            runCatching { player?.release() }
        }
        if (shouldContinue) {
            abandonAudioFocusIfNeeded()
            processNextSpeech()
        }
    }

    /** 停止并释放当前应用内播放器。 */
    private fun stopAndReleasePlayer() {
        val player = synchronized(lock) {
            val currentPlayer = mediaPlayer
            mediaPlayer = null
            currentPlayer
        }
        if (player != null) {
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.release() }
        }
        abandonAudioFocusIfNeeded()
    }

    /** 确保 TTS 文件合成缓存目录存在。 */
    private fun ensureSynthesisDir() {
        if (!synthesisDir.exists()) {
            synthesisDir.mkdirs()
        }
    }

    /** 清理队列中尚未播放的合成文件，调用方必须持有 lock。 */
    private fun clearQueuedFilesLocked() {
        while (true) {
            val request = pendingQueue.removeFirstOrNull() ?: return
            request.file.delete()
        }
    }

    /** 清理应用上次异常退出后遗留的语音合成缓存文件。 */
    private fun cleanSynthesisCache() {
        runCatching {
            synthesisDir.listFiles()?.forEach { file ->
                file.delete()
            }
        }
    }

    /** 申请短暂可降音的音频焦点，避免播报被背景音乐完全盖住。 */
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

    /** 关闭 TTS、应用内播放器，并清理队列、缓存、焦点和状态。 */
    fun shutdown() {
        Log.d(tag, "shutdown(): begin")
        synchronized(lock) {
            playbackGeneration += 1L
            clearQueuedFilesLocked()
            currentRequest?.file?.delete()
            currentRequest = null
            isSynthesizing = false
            isInitializing = false
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        stopAndReleasePlayer()
        cleanSynthesisCache()
        Log.d(tag, "shutdown(): done")
    }

    /** 停止当前播报并清空待播队列，但保留 TTS 初始化状态。 */
    fun stopSpeaking() {
        Log.d(tag, "stopSpeaking(): begin")
        synchronized(lock) {
            playbackGeneration += 1L
            clearQueuedFilesLocked()
            currentRequest?.file?.delete()
            currentRequest = null
            isSynthesizing = false
        }
        tts?.stop()
        stopAndReleasePlayer()
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

    /** TTS 初始化和合成重试常量。 */
    private companion object {
        /** 两次初始化尝试之间的最短间隔。 */
        const val INIT_RETRY_MIN_INTERVAL_MS = 1_000L

        /** 单条播报合成失败后的最大重试次数。 */
        const val MAX_SYNTHESIS_RETRY_COUNT = 1
    }
}
