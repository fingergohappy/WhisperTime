package com.example.whispertime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timerMode: String,
    val defaultDurationMs: Long? = null,
    val voiceIntervalMs: Long? = null,
    val prepareTimeSeconds: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
