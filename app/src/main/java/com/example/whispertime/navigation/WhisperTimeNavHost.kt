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
            arguments = listOf(navArgument("projectId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val projectIdString = backStackEntry.arguments?.getString("projectId")
            val projectId = if (projectIdString.isNullOrBlank()) null else projectIdString.toLongOrNull()
            
            ProjectEditScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Timer.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            if (arguments == null || !arguments.containsKey("projectId")) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
                return@composable
            }
            val projectId = arguments.getLong("projectId")
            TimerScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecords = { id -> navController.navigate(Screen.Records.createRoute(id)) }
            )
        }

        composable(
            route = Screen.Records.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            if (arguments == null || !arguments.containsKey("projectId")) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
                return@composable
            }
            val projectId = arguments.getLong("projectId")
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
            arguments = listOf(navArgument("recordId") {
                type = NavType.LongType
            })
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            if (arguments == null || !arguments.containsKey("recordId")) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
                return@composable
            }

            val recordId = arguments.getLong("recordId")
            RecordEditScreen(
                recordId = recordId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text)
    }
}
