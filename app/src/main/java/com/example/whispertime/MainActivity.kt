package com.example.whispertime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whispertime.navigation.WhisperTimeNavHost
import com.example.whispertime.ui.theme.WhisperTimeTheme

/**
 * 应用的主入口 Activity，采用 Single Activity 架构。
 *
 * 职责：
 * 1. 作为 Compose UI 的宿主容器。
 * 2. 初始化 Edge-to-Edge（沉浸式）显示效果。
 * 3. 挂载 [WhisperTimeNavHost] 处理全量页面路由跳转。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启全屏沉浸式体验，使 UI 内容能够伸展到状态栏和导航栏下方
        enableEdgeToEdge()
        setContent {
            WhisperTimeTheme {
                // 进入核心导航逻辑，负责根据路由分发各 Screen
                WhisperTimeNavHost()
            }
        }
    }
}
