package com.example.whispertime.ui.timer.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** 电池优化提示偏好文件名。 */
private const val BATTERY_EXEMPTION_PREFS = "battery_exemption_prompt"

/** 是否已经请求过忽略电池优化的标记 key。 */
private const val KEY_BATTERY_EXEMPTION_REQUESTED = "requested"

/** 必要时请求用户将应用加入电池优化白名单，以提高长时间后台计时稳定性。 */
internal fun requestBatteryOptimizationExemptionIfNeeded(context: Context) {
    val powerManager = context.getSystemService(PowerManager::class.java)
    if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return

    val preferences = context.getSharedPreferences(BATTERY_EXEMPTION_PREFS, Context.MODE_PRIVATE)
    if (preferences.getBoolean(KEY_BATTERY_EXEMPTION_REQUESTED, false)) return

    // 先记录已请求，避免用户拒绝后每次进入计时页都被打扰。
    preferences.edit().putBoolean(KEY_BATTERY_EXEMPTION_REQUESTED, true).apply()

    val packageUri = Uri.parse("package:${context.packageName}")
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = packageUri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = packageUri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val requestStarted = runCatching {
        context.startActivity(requestIntent)
    }.isSuccess
    if (!requestStarted) {
        // 部分 ROM 不支持白名单请求页时，退回到应用详情页让用户手动设置。
        runCatching {
            context.startActivity(fallbackIntent)
        }
    }
}
