package com.example.whispertime.service

import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState

/**
 * 活跃计时会话快照，用于前台服务被系统回收后恢复计时状态。
 *
 * @property projectId 当前计时所属项目主键。
 * @property projectName 当前计时所属项目名称。
 * @property mode 当前计时模式。
 * @property durationMs 倒计时总时长，正计时时为空。
 * @property voiceIntervalMs 语音播报间隔，未启用时为空。
 * @property vibrationEnabled 是否开启震动提醒。
 * @property state 保存时的计时状态。
 * @property prepareRemainingMs 准备倒计时剩余毫秒数。
 * @property prepareReferenceEpochMs 准备阶段保存时的墙钟时间。
 * @property prepareReferenceElapsedRealtimeMs 准备阶段保存时的单调时钟时间。
 * @property sessionStartEpochMs 正式计时开始的墙钟时间。
 * @property elapsedMs 已累计计时毫秒数。
 * @property runningReferenceElapsedRealtimeMs 运行阶段保存时的单调时钟时间。
 * @property lastAnnouncedElapsedMs 最近一次播报对应的已计时毫秒数。
 */
data class ActiveTimerSession(
    val projectId: Long,
    val projectName: String,
    val mode: TimerMode,
    val durationMs: Long?,
    val voiceIntervalMs: Long?,
    val vibrationEnabled: Boolean,
    val state: TimerState,
    val prepareRemainingMs: Long?,
    val prepareReferenceEpochMs: Long?,
    val prepareReferenceElapsedRealtimeMs: Long?,
    val sessionStartEpochMs: Long?,
    val elapsedMs: Long,
    val runningReferenceElapsedRealtimeMs: Long?,
    val lastAnnouncedElapsedMs: Long
)
