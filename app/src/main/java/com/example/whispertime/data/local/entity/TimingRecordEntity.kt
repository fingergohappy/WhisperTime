package com.example.whispertime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 表示一条计时记录。
 *
 * 与 [ProjectEntity] 存在多对一关系。
 *
 * @property id 唯一标识。
 * @property projectId 关联的项目 ID（外键）。
 * @property startTime 开始时间戳。
 * @property endTime 结束时间戳。
 * @property durationMs 时长（毫秒）。
 * @property createdAt 记录创建时间戳。
 */
@Entity(
    tableName = "timing_records",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class TimingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val createdAt: Long
)
