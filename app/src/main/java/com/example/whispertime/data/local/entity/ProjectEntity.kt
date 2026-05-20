package com.example.whispertime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 项目持久化实体，保存计时模式和默认提醒配置。
 *
 * @property id 项目主键，新增时由 Room 自动生成。
 * @property name 项目名称。
 * @property timerMode 计时模式字符串，对应 [com.example.whispertime.timer.TimerMode]。
 * @property defaultDurationMs 倒计时默认时长，正计时时为空。
 * @property voiceIntervalMs 语音播报间隔，未启用时为空。
 * @property vibrationEnabled 是否开启震动提醒。
 * @property prepareTimeSeconds 启动前准备倒计时秒数，未启用时为空。
 * @property createdAt 创建时间戳。
 * @property updatedAt 最近更新时间戳。
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timerMode: String,
    val defaultDurationMs: Long?,
    val voiceIntervalMs: Long?,
    val vibrationEnabled: Boolean = false,
    val prepareTimeSeconds: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
