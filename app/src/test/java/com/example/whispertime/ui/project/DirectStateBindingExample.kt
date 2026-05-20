package com.example.whispertime.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** 不使用 ViewModel 的直接状态绑定示例，状态只保存在当前 Composable 内。 */
@Composable
fun DirectStateBindingExample() {
    // 项目名称输入框状态；修改它会触发读取 projectName 的 UI 重新组合。
    var projectName by remember { mutableStateOf("") }

    // 计时模式状态；用于控制 Chip 选中态和倒计时时长输入框是否显示。
    var timerMode by remember { mutableStateOf("COUNT_UP") }

    // 倒计时默认时长状态；只在倒计时模式下参与 UI 展示和保存按钮校验。
    var defaultDuration by remember { mutableStateOf("") }

    // 震动提醒开关状态；Switch 会读取并修改这个值。
    var vibrationEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = projectName,
            onValueChange = { projectName = it },
            label = { Text("项目名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Column {
            Text(
                text = "计时模式",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row {
                FilterChip(
                    selected = timerMode == "COUNT_UP",
                    onClick = { timerMode = "COUNT_UP" },
                    label = { Text("正计时") },
                    leadingIcon = if (timerMode == "COUNT_UP") {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = timerMode == "COUNTDOWN",
                    onClick = { timerMode = "COUNTDOWN" },
                    label = { Text("倒计时") },
                    leadingIcon = if (timerMode == "COUNTDOWN") {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }

        if (timerMode == "COUNTDOWN") {
            OutlinedTextField(
                value = defaultDuration,
                onValueChange = {
                    // 倒计时时长只接受数字；状态变化后输入框和按钮状态会自动刷新。
                    if (it.all { char -> char.isDigit() }) {
                        defaultDuration = it
                    }
                },
                label = { Text("默认时长（分钟）") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = defaultDuration.isEmpty()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("开启震动提醒", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = vibrationEnabled,
                onCheckedChange = { vibrationEnabled = it }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                // 这里没有 ViewModel，所以点击保存只演示从本地状态读取当前表单值。
                val currentName = projectName.trim()
                val currentMode = timerMode
                val currentDuration = defaultDuration.trim()
                val currentVibrationEnabled = vibrationEnabled

                println(
                    "name=$currentName, mode=$currentMode, " +
                        "duration=$currentDuration, vibration=$currentVibrationEnabled"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = projectName.isNotBlank() &&
                (timerMode != "COUNTDOWN" || defaultDuration.isNotBlank())
        ) {
            Text("保存")
        }
    }
}
