package com.example.whispertime.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.whispertime.ui.project.ProjectListScreen

@Composable
fun WhisperTimeNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.ProjectList.route
    ) {
        composable(Screen.ProjectList.route) {
            ProjectListScreen(
                onNavigateToTimer = {},
                onNavigateToRecords = {},
                onNavigateToEdit = {}
            )
        }
    }
}
