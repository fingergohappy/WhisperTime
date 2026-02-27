package com.example.whispertime.navigation

sealed class Screen(val route: String) {
    /**
     * Home screen showing list of projects
     */
    object ProjectList : Screen("project_list")

    /**
     * Create or edit a project.
     * @param projectId Project ID (optional, null for creation)
     */
    object ProjectEdit : Screen("project_edit?projectId={projectId}") {
        fun createRoute(projectId: Long? = null) =
            projectId?.let { "project_edit?projectId=$it" } ?: "project_edit"
    }

    /**
     * Timer screen for a specific project.
     * @param projectId Project ID
     */
    object Timer : Screen("timer/{projectId}") {
        fun createRoute(projectId: Long) = "timer/$projectId"
    }

    /**
     * History records list for a specific project.
     * @param projectId Project ID
     */
    object Records : Screen("records/{projectId}") {
        fun createRoute(projectId: Long) = "records/$projectId"
    }

    /**
     * Edit a specific timing record.
     * @param recordId Record ID
     */
    object RecordEdit : Screen("record_edit/{recordId}") {
        fun createRoute(recordId: Long) = "record_edit/$recordId"
    }
}
