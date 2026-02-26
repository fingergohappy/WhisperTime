package com.example.whispertime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.whispertime.navigation.WhisperTimeNavHost
import com.example.whispertime.ui.theme.WhisperTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhisperTimeTheme {
                WhisperTimeNavHost()
            }
        }
    }
}
