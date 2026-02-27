package com.example.whispertime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.whispertime.navigation.WhisperTimeNavHost
import com.example.whispertime.ui.theme.WhisperTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhisperTimeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WhisperTimeNavHost()
                }
            }
        }
    }
}
