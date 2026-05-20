package com.example.whispertime

import android.app.Application
import com.example.whispertime.di.AppContainer

/** 应用级入口，持有全局依赖容器。 */
class WhisperTimeApplication : Application() {
    /** 全局依赖容器，在应用启动时创建并对外只读暴露。 */
    lateinit var container: AppContainer
        private set

    /** 创建数据库、仓库、计时器、语音和震动等全局依赖。 */
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
