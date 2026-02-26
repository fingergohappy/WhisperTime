package com.example.whispertime.navigation

import androidx.compose.runtime.Composable
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
                    navController.navigate(Screen.Timer(projectId).route)
                },
                onNavigateToRecords = { projectId ->
                    navController.navigate(Screen.RecordList(projectId).route)
                },
                onNavigateToEdit = { projectId ->
                    val route = if (projectId != null) {
                        Screen.ProjectEdit(projectId).route
                    } else {
                        Screen.ProjectEdit(null).route
                    }
                    navController.navigate(route)
                }
            )
        }
        
        composable(
            route = Screen.ProjectEdit.ROUTE,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "new"
                }
            )
        ) { backStackEntry ->
            val projectIdStr = backStackEntry.arguments?.getString("projectId")
            val projectId = if (projectIdStr == "new" || projectIdStr == null) null else projectIdStr.toLongOrNull()
            
            ProjectEditScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.Timer.ROUTE,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            TimerScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecords = { pid ->
                    navController.navigate(Screen.RecordList(pid).route) {
                        popUpTo(Screen.Timer.ROUTE) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.RecordList.ROUTE,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            RecordListScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { recordId ->
                    navController.navigate(Screen.RecordEdit(recordId).route)
                }
            )
        }
        
        composable(
            route = Screen.RecordEdit.ROUTE,
            arguments = listOf(
                navArgument("recordId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: return@composable
            RecordEditScreen(
                recordId = recordId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
