package com.example.whispertime

import android.app.Application
import com.example.whispertime.di.AppContainer

class WhisperTimeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.clear()
        super.onTerminate()
    }
}
