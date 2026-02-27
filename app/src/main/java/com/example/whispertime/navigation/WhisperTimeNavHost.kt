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

/**
 * 应用导航图配置中心
 * 负责定义所有 Composable 屏幕的路由跳转逻辑、参数传递及回退栈管理
 */
@Composable
fun WhisperTimeNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.ProjectList.route
    ) {
        // --- 项目列表 ---
        composable(Screen.ProjectList.route) {
            ProjectListScreen(
                onNavigateToTimer = { projectId ->
                    // 跳转至计时工作台
                    navController.navigate(Screen.Timer(projectId).route)
                },
                onNavigateToRecords = { projectId ->
                    // 跳转至项目历史记录
                    navController.navigate(Screen.RecordList(projectId).route)
                },
                onNavigateToEdit = { projectId ->
                    // 跳转至项目编辑/创建页
                    val route = if (projectId != null) {
                        Screen.ProjectEdit(projectId).route
                    } else {
                        Screen.ProjectEdit(null).route
                    }
                    navController.navigate(route)
                }
            )
        }

        // --- 项目编辑/新增 ---
        composable(
            route = Screen.ProjectEdit.ROUTE,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = "new" // 使用 "new" 标识符代表新增项目
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

        // --- 计时工作台 ---
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
                    // 计时结束后跳转至记录列表，并清除计时页的堆栈
                    navController.navigate(Screen.RecordList(pid).route) {
                        popUpTo(Screen.Timer.ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // --- 历史记录列表 ---
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

        // --- 记录编辑 ---
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
