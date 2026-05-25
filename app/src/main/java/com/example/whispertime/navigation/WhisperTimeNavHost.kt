package com.example.whispertime.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.whispertime.ui.project.ProjectEditScreen
import com.example.whispertime.ui.record.RecordEditScreen
import com.example.whispertime.ui.record.RecordListScreen
import com.example.whispertime.ui.timer.TimerHomeScreen
import com.example.whispertime.ui.timer.TimerScreen

/** 判断项目切换方向，用于计时页左右滑动转场。 */
private fun isForwardProjectSwitch(initialProjectId: Long?, targetProjectId: Long?): Boolean? {
    if (initialProjectId == null || targetProjectId == null || initialProjectId == targetProjectId) {
        return null
    }
    return targetProjectId > initialProjectId
}

/** 应用导航宿主，集中声明所有页面路由和页面间跳转。 */
@Composable
fun WhisperTimeNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.TimerHome.route
    ) {
        // 计时首页路由，作为应用启动后的默认入口。
        composable(Screen.TimerHome.route) {
            // 计时首页界面，负责分发进入计时页和新建项目的导航事件。
            TimerHomeScreen(
                // 进入指定项目的计时页。
                onNavigateToTimer = { projectId ->
                    // 跳转到项目计时页。
                    navController.navigate(Screen.Timer(projectId).route) {
                        // 进入计时页后移除首页，避免返回时再次看到入口页。
                        popUpTo(Screen.TimerHome.route) { inclusive = true }
                    }
                },
                // 进入新建项目页面。
                onNavigateToCreateProject = {
                    // projectId 为空表示创建新项目。
                    navController.navigate(Screen.ProjectEdit(null).route)
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
            // "new" 代表新建项目，数字字符串代表编辑已有项目。
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
            ),
            enterTransition = {
                val initialProjectId = initialState.arguments?.getLong("projectId")
                val targetProjectId = targetState.arguments?.getLong("projectId")
                // 相邻项目切换时按项目顺序决定左右滑入方向。
                when (isForwardProjectSwitch(initialProjectId, targetProjectId)) {
                    true -> slideInHorizontally(animationSpec = tween(240)) { it / 3 } + fadeIn(animationSpec = tween(220))
                    false -> slideInHorizontally(animationSpec = tween(240)) { -it / 3 } + fadeIn(animationSpec = tween(220))
                    null -> fadeIn(animationSpec = tween(180))
                }
            },
            exitTransition = {
                val initialProjectId = initialState.arguments?.getLong("projectId")
                val targetProjectId = targetState.arguments?.getLong("projectId")
                // 退出动画与进入方向相反，保持项目切换的空间感。
                when (isForwardProjectSwitch(initialProjectId, targetProjectId)) {
                    true -> slideOutHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeOut(animationSpec = tween(180))
                    false -> slideOutHorizontally(animationSpec = tween(220)) { it / 3 } + fadeOut(animationSpec = tween(180))
                    null -> fadeOut(animationSpec = tween(120))
                }
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: return@composable
            TimerScreen(
                projectId = projectId,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.TimerHome.route)
                    }
                },
                onNavigateToRecords = { pid ->
                    navController.navigate(Screen.RecordList(pid).route) {
                        popUpTo(Screen.Timer.ROUTE) { inclusive = true }
                    }
                },
                onNavigateToTimer = { pid ->
                    navController.navigate(Screen.Timer(pid).route) {
                        popUpTo(Screen.Timer.ROUTE) { inclusive = true }
                    }
                },
                onNavigateToCreateProject = {
                    navController.navigate(Screen.ProjectEdit(null).route)
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
