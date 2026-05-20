package com.example.whispertime.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 震动提醒管理器，封装不同 Android 版本的 Vibrator 获取和触发。 */
class VibrationManager(context: Context) {

    /** 当前设备可用的震动器，设备不支持时为空。 */
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** 触发一次短震动提醒。 */
    fun vibrateReminder() {
        val currentVibrator = vibrator ?: return
        if (!currentVibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android O 及以上使用 VibrationEffect 以获得一致震动时长。
            currentVibrator.vibrate(
                VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            currentVibrator.vibrate(180L)
        }
    }

    /** 取消当前仍在执行的震动。 */
    fun cancel() {
        vibrator?.cancel()
    }
}
