package com.example.whispertime.ui.timer

import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.timer.TimerMode
import org.junit.Assert.assertTrue
import org.junit.Test

/** 项目实体转计时配置测试。 */
class ProjectTimerConfigTest {

    /** 验证项目震动开关会传递到计时配置。 */
    @Test
    fun toTimerConfig_carriesVibrationSetting() {
        val project = ProjectEntity(
            id = 9L,
            name = "Study",
            timerMode = TimerMode.COUNT_UP.name,
            defaultDurationMs = null,
            voiceIntervalMs = 30_000L,
            vibrationEnabled = true,
            prepareTimeSeconds = 3L,
            createdAt = 1L,
            updatedAt = 2L
        )

        val config = project.toTimerConfig()

        assertTrue(config.vibrationEnabled)
    }
}
