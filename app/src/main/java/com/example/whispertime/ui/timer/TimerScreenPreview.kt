package com.example.whispertime.ui.timer

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.timer.TimerConfig
import com.example.whispertime.timer.TimerMode
import com.example.whispertime.timer.TimerState
import com.example.whispertime.ui.theme.WhisperTimeTheme

/** 计时页 Preview 集合，用固定假数据预览纯界面内容。 */
class TimerScreenPreview {

    /** 深色模式下的计时页空闲状态预览。 */
    @Preview(
        name = "计时页深色",
        showBackground = true,
        uiMode = Configuration.UI_MODE_NIGHT_YES
    )
    @Composable
    fun TimerScreenDarkPreview() {
        val projectId = 1L
        val projects = previewProjects()
        val records = previewRecords(projectId)

        WhisperTimeTheme {
            TimerScreenContent(
                projectId = projectId,
                timerState = TimerState.IDLE,
                elapsedMs = 0L,
                remainingMs = null,
                prepareRemainingMs = null,
                config = TimerConfig(
                    projectId = projectId,
                    projectName = "深色模式",
                    mode = TimerMode.COUNT_UP,
                    voiceIntervalMs = 60_000L,
                    vibrationEnabled = true,
                    prepareTimeMs = 5_000L
                ),
                projectName = "深色模式",
                projects = projects,
                records = records,
                totalDurationMs = records.sumOf { it.durationMs },
                recordCount = records.size,
                averageDurationMs = records.map { it.durationMs }.average().toLong(),
                weeklyStats = previewWeeklyStats(),
                onNavigateToTimer = {},
                onNavigateToCreateProject = {},
                onUpdateProjectConfig = { _, _, _, _, _ -> },
                onCancelTimer = {},
                onPauseTimer = {},
                onResumeTimer = {},
                onStartTimer = { _, _, _, _, _ -> },
                onStopTimer = {},
                onDeleteCurrentProject = {},
                onDeleteRecord = {},
                onUpdateRecordDurationSeconds = { _, _ -> }
            )
        }
    }

    /** 创建 Preview 使用的项目列表。 */
    private fun previewProjects(): List<ProjectEntity> {
        val createdAt = 1_714_000_000_000L
        return listOf(
            ProjectEntity(
                id = 1L,
                name = "深色模式",
                timerMode = TimerMode.COUNT_UP.name,
                defaultDurationMs = null,
                voiceIntervalMs = 60_000L,
                vibrationEnabled = true,
                prepareTimeSeconds = 5L,
                createdAt = createdAt,
                updatedAt = createdAt
            ),
            ProjectEntity(
                id = 2L,
                name = "专注阅读",
                timerMode = TimerMode.COUNTDOWN.name,
                defaultDurationMs = 25 * 60 * 1000L,
                voiceIntervalMs = null,
                vibrationEnabled = false,
                prepareTimeSeconds = 3L,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        )
    }

    /** 创建 Preview 使用的计时记录列表。 */
    private fun previewRecords(projectId: Long): List<TimingRecordEntity> {
        val baseTime = 1_714_000_000_000L
        return listOf(
            TimingRecordEntity(
                id = 1L,
                projectId = projectId,
                startTime = baseTime,
                endTime = baseTime + 48 * 60 * 1000L,
                durationMs = 48 * 60 * 1000L,
                createdAt = baseTime
            ),
            TimingRecordEntity(
                id = 2L,
                projectId = projectId,
                startTime = baseTime + 2 * 60 * 60 * 1000L,
                endTime = baseTime + 2 * 60 * 60 * 1000L + 35 * 60 * 1000L,
                durationMs = 35 * 60 * 1000L,
                createdAt = baseTime
            )
        )
    }

    /** 创建 Preview 使用的最近七天统计数据。 */
    private fun previewWeeklyStats(): List<TimerViewModel.WeeklyStat> {
        return listOf(
            TimerViewModel.WeeklyStat("Mon", 25 * 60 * 1000L),
            TimerViewModel.WeeklyStat("Tue", 45 * 60 * 1000L),
            TimerViewModel.WeeklyStat("Wed", 20 * 60 * 1000L),
            TimerViewModel.WeeklyStat("Thu", 60 * 60 * 1000L),
            TimerViewModel.WeeklyStat("Fri", 35 * 60 * 1000L),
            TimerViewModel.WeeklyStat("Sat", 50 * 60 * 1000L),
            TimerViewModel.WeeklyStat("Sun", 30 * 60 * 1000L)
        )
    }
}
