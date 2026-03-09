package com.example.whispertime.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun WhisperTimeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
