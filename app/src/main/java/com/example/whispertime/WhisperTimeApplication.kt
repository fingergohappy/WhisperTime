package com.example.whispertime

import android.app.Application
import com.example.whispertime.di.AppContainer

/**
 * 应用程序类，作为 App 的全局入口和生命周期容器。
 *
 * 职责：
 * 1. 初始化并持有全局唯一的 [AppContainer]，实现简易的手动依赖注入。
 * 2. 管理整个应用的生命周期，其 onCreate 在所有 Activity/Service 之前调用。
 */
class WhisperTimeApplication : Application() {
    /**
     * 全局依赖容器，在应用启动时初始化，为 ViewModel 和 Service 提供依赖项。
     */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 在应用启动时创建容器，确保所有组件都能访问到单例依赖
        container = AppContainer(this)
    }
}
