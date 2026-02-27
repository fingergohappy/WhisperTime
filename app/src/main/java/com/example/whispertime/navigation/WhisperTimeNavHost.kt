package com.example.whispertime.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.whispertime.ui.project.ProjectEditScreen
import com.example.whispertime.ui.project.ProjectListScreen
import com.example.whispertime.ui.record.RecordEditScreen
import com.example.whispertime.ui.record.RecordListScreen
import com.example.whispertime.ui.timer.TimerScreen

@Composable
fun WhisperTimeNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.ProjectList.route
    ) {
        composable(Screen.ProjectList.route) {
            ProjectListScreen(
                onNavigateToTimer = { projectId ->
                    navController.navigate(Screen.Timer.createRoute(projectId))
                },
                onNavigateToRecords = { projectId ->
                    navController.navigate(Screen.Records.createRoute(projectId))
                },
                onNavigateToEdit = { projectId ->
                    navController.navigate(Screen.ProjectEdit.createRoute(projectId))
                }
            )
        }
        
        composable(
            route = Screen.ProjectEdit.route,
            arguments = listOf(navArgument("id") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val idString = backStackEntry.arguments?.getString("id")
            val projectId = if (idString.isNullOrBlank()) null else idString.toLongOrNull()
            
            ProjectEditScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Timer.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("id") ?: return@composable
            TimerScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecords = { id -> navController.navigate(Screen.Records.createRoute(id)) }
            )
        }

        composable(
            route = Screen.Records.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("id") ?: return@composable
            RecordListScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { recordId ->
                    navController.navigate(Screen.RecordEdit.createRoute(recordId))
                }
            )
        }

        composable(
            route = Screen.RecordEdit.route,
            arguments = listOf(navArgument("id") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val idString = backStackEntry.arguments?.getString("id")
            val recordId = if (idString.isNullOrBlank()) null else idString.toLongOrNull()
            
            if (recordId != null) {
                RecordEditScreen(
                    recordId = recordId,
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text)
    }
}
