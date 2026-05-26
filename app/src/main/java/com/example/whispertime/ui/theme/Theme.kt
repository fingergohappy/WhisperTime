package com.example.whispertime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat

/** 深色模式配色方案。 */
private val DarkColorScheme = darkColorScheme(
    primary = Champagne300,
    onPrimary = Graphite990,
    primaryContainer = Champagne700,
    onPrimaryContainer = Porcelain50,
    secondary = SignalBlue300,
    onSecondary = Graphite990,
    secondaryContainer = SignalBlue800,
    onSecondaryContainer = Porcelain50,
    tertiary = Champagne500,
    onTertiary = Graphite990,
    tertiaryContainer = Graphite800,
    onTertiaryContainer = Porcelain50,
    error = Danger400,
    onError = Porcelain50,
    background = Graphite990,
    onBackground = Porcelain50,
    surface = Graphite900,
    onSurface = Porcelain50,
    surfaceVariant = Graphite800,
    onSurfaceVariant = Stone300,
    outline = Graphite700,
    outlineVariant = Graphite800
)

/** 浅色模式配色方案，用于后续切换系统亮色时保持同一套品牌色。 */
private val LightColorScheme = lightColorScheme(
    primary = Champagne700,
    onPrimary = Porcelain50,
    primaryContainer = Porcelain100,
    onPrimaryContainer = Ink900,
    secondary = SignalBlue600,
    onSecondary = Porcelain50,
    secondaryContainer = Porcelain100,
    onSecondaryContainer = Ink900,
    tertiary = Champagne500,
    onTertiary = Graphite990,
    tertiaryContainer = Porcelain100,
    onTertiaryContainer = Ink900,
    error = Danger400,
    onError = Porcelain50,
    background = Porcelain100,
    onBackground = Ink900,
    surface = PaperWhite,
    onSurface = Ink900,
    surfaceVariant = Stone200,
    onSurfaceVariant = Stone600,
    outline = Stone200,
    outlineVariant = Porcelain100
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
