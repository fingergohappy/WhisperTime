package com.example.whispertime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 表示一个计时项目。
 *
 * @property id 唯一标识，由数据库自增生成。
 * @property name 项目名称。
 * @property timerMode 计时模式（正计时、倒计时）。
 * @property defaultDurationMs 默认时长（对于倒计时模式有效）。
 * @property voiceIntervalMs 语音播报间隔（毫秒）。
 * @property prepareTimeSeconds 准备时间（秒）。
 * @property createdAt 创建时间的时间戳。
 * @property updatedAt 最后更新时间的时间戳。
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timerMode: String,
    val defaultDurationMs: Long?,
    val voiceIntervalMs: Long?,
    val prepareTimeSeconds: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
