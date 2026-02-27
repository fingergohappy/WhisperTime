package com.example.whispertime.ui.record

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 历史记录编辑屏幕
 * 允许用户手动修正计时的开始时间、结束时间或持续时长，支持字段间的自动联动更新
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(
    recordId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: RecordEditViewModel = viewModel(
        factory = RecordEditViewModel.factory(application, recordId)
    )

    // 绑定 ViewModel 中的表单文本状态
    val startTime by viewModel.startTimeText.collectAsState()
    val endTime by viewModel.endTimeText.collectAsState()
    val duration by viewModel.durationText.collectAsState()

    // 监听保存成功后的导航反馈
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
                title = { Text("编辑记录") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 开始时间输入框
            OutlinedTextField(
                value = startTime,
                onValueChange = viewModel::onStartTimeChanged,
                label = { Text("开始时间") },
                placeholder = { Text("yyyy-MM-dd HH:mm:ss") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 结束时间输入框
            OutlinedTextField(
                value = endTime,
                onValueChange = viewModel::onEndTimeChanged,
                label = { Text("结束时间") },
                placeholder = { Text("yyyy-MM-dd HH:mm:ss") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 持续时长输入框 (格式: MM:SS)
            OutlinedTextField(
                value = duration,
                onValueChange = viewModel::onDurationChanged,
                label = { Text("持续时长 (MM:SS)") },
                placeholder = { Text("MM:SS") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 联动更新提示
            Text(
                text = "修改任一字段，其他字段可能自动联动更新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮
            Button(
                onClick = viewModel::saveRecord,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}
