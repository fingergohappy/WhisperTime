package com.example.whispertime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

/** 深色模式配色方案。 */
private val DarkColorScheme = darkColorScheme(
    primary = Indigo400,
    onPrimary = Mist50,
    secondary = Cyan400,
    onSecondary = Obsidian950,
    tertiary = Success400,
    onTertiary = Obsidian950,
    error = Danger400,
    onError = Mist50,
    background = Obsidian950,
    onBackground = Mist50,
    surface = Obsidian900,
    onSurface = Mist50,
    surfaceVariant = Obsidian800,
    onSurfaceVariant = Mist400,
    outline = Obsidian700
)

/** 浅色模式配色方案，目前沿用深色视觉基调。 */
private val LightColorScheme = lightColorScheme(
    primary = Indigo500,
    onPrimary = Mist50,
    secondary = Cyan400,
    onSecondary = Obsidian950,
    tertiary = Success400,
    onTertiary = Obsidian950,
    error = Danger400,
    onError = Mist50,
    background = Obsidian950,
    onBackground = Mist50,
    surface = Obsidian900,
    onSurface = Mist50,
    surfaceVariant = Obsidian800,
    onSurfaceVariant = Mist400,
    outline = Obsidian700
)

/** 应用主题入口，统一 Material 配色、字体和系统导航栏颜色。 */
@Composable
fun WhisperTimeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    /** 当前实际使用的 Material 配色。 */
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 让系统导航栏颜色和应用背景保持一致，避免底部出现突兀色块。
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            window.navigationBarColor = colorScheme.background.value.toInt()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
