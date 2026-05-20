package com.example.whispertime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.whispertime.navigation.WhisperTimeNavHost
import com.example.whispertime.ui.theme.WhisperTimeTheme

/** 应用唯一 Activity，负责挂载 Compose 主题和导航入口。 */
class MainActivity : ComponentActivity() {
    /** 初始化沉浸式窗口并创建应用的 Compose 内容树。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 将 Compose 界面内容挂载到当前 Activity，后续的主题、Surface 和导航都从这里开始渲染。
        setContent {
            WhisperTimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WhisperTimeNavHost()
                }
            }
        }
    }
}
