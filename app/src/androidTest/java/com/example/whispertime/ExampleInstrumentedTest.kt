package com.example.whispertime

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/** Android 仪器测试示例，运行在真机或模拟器上。 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    /** 验证测试环境能读取到应用包名。 */
    @Test
    fun useAppContext() {
        // 获取被测应用的 Context。
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.whispertime", appContext.packageName)
    }
}
