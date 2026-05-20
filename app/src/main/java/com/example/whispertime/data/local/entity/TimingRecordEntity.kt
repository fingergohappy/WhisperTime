package com.example.whispertime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计时记录持久化实体，和项目表通过 projectId 建立级联关系。
 *
 * @property id 记录主键，新增时由 Room 自动生成。
 * @property projectId 所属项目主键。
 * @property startTime 计时开始的墙钟时间戳。
 * @property endTime 计时结束的墙钟时间戳。
 * @property durationMs 本次计时持续毫秒数。
 * @property createdAt 记录写入时间戳。
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
