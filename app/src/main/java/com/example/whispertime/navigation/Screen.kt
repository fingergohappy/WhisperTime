package com.example.whispertime.navigation

sealed class Screen(val route: String) {
    object ProjectList : Screen("project_list")
}
