package com.example.whispertime.navigation

sealed class Screen(val route: String) {
    object ProjectList : Screen("project_list")
    object ProjectEdit : Screen("project_edit?id={id}") {
        fun createRoute(id: Long? = null) = "project_edit?id=${id ?: ""}"
    }
    object Timer : Screen("timer/{id}") {
        fun createRoute(id: Long) = "timer/$id"
    }
    object Records : Screen("records/{id}") {
        fun createRoute(id: Long) = "records/$id"
    }

    object RecordEdit : Screen("record_edit?id={id}") {
        fun createRoute(id: Long? = null) = "record_edit?id=${id ?: ""}"
    }
}
