package com.example.whispertime.ui.timer.screen

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 过滤输入文本中的非数字字符。 */
internal fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

/** 将秒数格式化为计时圆盘使用的 mm:ss 或 hh:mm:ss。 */
internal fun formatLarge(totalSecondsInput: Long): String {
    val totalSeconds = totalSecondsInput.coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/** 将毫秒时长格式化为固定 hh:mm:ss。 */
internal fun formatDurationHms(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

/** 将时间戳格式化为月/日和时分。 */
internal fun formatDate(epochMs: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(epochMs))
}

/** 将时间戳格式化为时分。 */
internal fun formatClock(epochMs: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}
