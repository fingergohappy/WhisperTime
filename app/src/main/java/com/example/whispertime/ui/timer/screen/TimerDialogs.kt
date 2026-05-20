package com.example.whispertime.ui.timer.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.whispertime.data.local.entity.TimingRecordEntity

/** 修改记录时长弹窗。 */
@Composable
internal fun EditRecordDurationDialog(
    record: TimingRecordEntity,
    editDurationSeconds: String,
    onEditDurationSecondsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (TimingRecordEntity, Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改记录时长") },
        text = {
            OutlinedTextField(
                value = editDurationSeconds,
                onValueChange = { onEditDurationSecondsChange(digitsOnly(it)) },
                label = { Text("总秒数") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val seconds = editDurationSeconds.toLongOrNull()
                    if (seconds != null && seconds > 0) {
                        onConfirm(record, seconds)
                    }
                    onDismiss()
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/** 删除项目确认弹窗。 */
@Composable
internal fun DeleteProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除项目") },
        text = { Text("确定要删除当前项目吗？项目下的记录也会一并删除，此操作不可撤销。") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                }
            ) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
