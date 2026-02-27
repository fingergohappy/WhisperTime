package com.example.whispertime.navigation

sealed class Screen(val route: String) {
    object ProjectList : Screen("project_list")
    
    data class ProjectEdit(val projectId: Long? = null) : Screen(
        if (projectId != null) "project_edit/$projectId" else "project_edit/new"
    ) {
        companion object {
            const val ROUTE = "project_edit/{projectId}"
        }
    }
    
    data class Timer(val projectId: Long) : Screen("timer/$projectId") {
        companion object {
            const val ROUTE = "timer/{projectId}"
        }
    }
    
    data class RecordList(val projectId: Long) : Screen("record_list/$projectId") {
        companion object {
            const val ROUTE = "record_list/{projectId}"
        }
    }
    
    data class RecordEdit(val recordId: Long) : Screen("record_edit/$recordId") {
        companion object {
            const val ROUTE = "record_edit/{recordId}"
        }
    }
}
