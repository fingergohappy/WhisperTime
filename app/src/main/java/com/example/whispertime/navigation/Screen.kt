package com.example.whispertime.navigation

sealed class Screen(val route: String) {
    /**
     * Home screen showing list of projects
     */
    object ProjectList : Screen("project_list")

    /**
     * Create or edit a project.
     * @param id Project ID (optional, null for creation)
     */
    object ProjectEdit : Screen("project_edit?id={id}") {
        fun createRoute(id: Long? = null) = "project_edit?id=${id ?: ""}"
    }

    /**
     * Timer screen for a specific project.
     * @param id Project ID
     */
    object Timer : Screen("timer/{id}") {
        fun createRoute(id: Long) = "timer/$id"
    }

    /**
     * History records list for a specific project.
     * @param id Project ID
     */
    object Records : Screen("records/{id}") {
        fun createRoute(id: Long) = "records/$id"
    }

    /**
     * Edit a specific timing record.
     * @param id Record ID
     */
    object RecordEdit : Screen("record_edit?id={id}") {
        fun createRoute(id: Long? = null) = "record_edit?id=${id ?: ""}"
    }
}
