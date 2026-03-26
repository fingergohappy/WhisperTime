package com.example.whispertime.ui.project

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditScreen(
    projectId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: ProjectEditViewModel = viewModel(
        factory = ProjectEditViewModel.factory(
            LocalContext.current.applicationContext as Application,
            projectId
        )
    )
) {
    val projectName by viewModel.projectName.collectAsState()
    val timerMode by viewModel.timerMode.collectAsState()
    val defaultDuration by viewModel.defaultDurationMinutes.collectAsState()
    val voiceInterval by viewModel.voiceIntervalSeconds.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val prepareTime by viewModel.prepareTimeSeconds.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saveResult.collect { success ->
            if (success) {
                onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "编辑项目" else "新建项目") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = projectName,
                onValueChange = { viewModel.projectName.value = it },
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
                        onClick = { viewModel.timerMode.value = "COUNT_UP" },
                        label = { Text("正计时") },
                        leadingIcon = if (timerMode == "COUNT_UP") {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = timerMode == "COUNTDOWN",
                        onClick = { viewModel.timerMode.value = "COUNTDOWN" },
                        label = { Text("倒计时") },
                        leadingIcon = if (timerMode == "COUNTDOWN") {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
            }

            if (timerMode == "COUNTDOWN") {
                OutlinedTextField(
                    value = defaultDuration,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() }) {
                            viewModel.defaultDurationMinutes.value = it
                        }
                    },
                    label = { Text("默认时长（分钟）") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = {
                        if (defaultDuration.isEmpty() && timerMode == "COUNTDOWN") {
                            Text("必填项", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    isError = defaultDuration.isEmpty()
                )
            }

            OutlinedTextField(
                value = voiceInterval,
                onValueChange = {
                    if (it.all { char -> char.isDigit() }) {
                        viewModel.voiceIntervalSeconds.value = it
                    }
                },
                label = { Text("语音播报间隔（秒）") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("可选") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开启震动提醒", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "复用语音播报间隔；准备倒计时、开始和结束也会震动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = { viewModel.vibrationEnabled.value = it }
                )
            }

            OutlinedTextField(
                value = prepareTime,
                onValueChange = {
                    if (it.all { char -> char.isDigit() }) {
                        viewModel.prepareTimeSeconds.value = it
                    }
                },
                label = { Text("准备时间（秒）") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("可选，计时前倒计时") }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.saveProject() },
                modifier = Modifier.fillMaxWidth(),
                enabled = projectName.isNotBlank() && (timerMode != "COUNTDOWN" || defaultDuration.isNotBlank())
            ) {
                Text("保存")
            }
        }
    }
}
