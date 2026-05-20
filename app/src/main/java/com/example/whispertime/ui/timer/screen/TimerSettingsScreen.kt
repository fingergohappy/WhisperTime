package com.example.whispertime.ui.timer.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.whispertime.timer.TimerMode

/** 项目设置页，负责编辑当前项目的默认计时和提醒配置。 */
@Composable
internal fun TimerSettingsScreen(
    selectedMode: TimerMode,
    prepareSecondsText: String,
    voiceIntervalSecondsText: String,
    voiceEnabled: Boolean,
    vibrationReminderEnabled: Boolean,
    onSelectedModeChange: (TimerMode) -> Unit,
    onPrepareSecondsTextChange: (String) -> Unit,
    onVoiceIntervalSecondsTextChange: (String) -> Unit,
    onVoiceEnabledChange: (Boolean) -> Unit,
    onVibrationReminderEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDeleteProject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("项目设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }

        SettingsCard {
            Text("准备倒计时（秒）", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = prepareSecondsText,
                onValueChange = { onPrepareSecondsTextChange(digitsOnly(it)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("语音播报", style = MaterialTheme.typography.labelLarge)
                Switch(checked = voiceEnabled, onCheckedChange = onVoiceEnabledChange)
            }
            OutlinedTextField(
                value = voiceIntervalSecondsText,
                onValueChange = { onVoiceIntervalSecondsTextChange(digitsOnly(it)) },
                singleLine = true,
                enabled = voiceEnabled,
                label = { Text("间隔（秒）") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("震动提醒", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = vibrationReminderEnabled,
                    onCheckedChange = onVibrationReminderEnabledChange
                )
            }
            Text(
                text = "周期震动复用语音播报间隔；准备倒计时、开始和结束也会震动",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard {
            Text("计时模式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedMode == TimerMode.COUNT_UP,
                    onClick = { onSelectedModeChange(TimerMode.COUNT_UP) },
                    label = { Text("正计时") }
                )
                FilterChip(
                    selected = selectedMode == TimerMode.COUNTDOWN,
                    onClick = { onSelectedModeChange(TimerMode.COUNTDOWN) },
                    label = { Text("倒计时") }
                )
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存设置")
        }

        TextButton(
            onClick = onDeleteProject,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text("删除项目")
        }
    }
}

/** 设置页通用卡片容器。 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
