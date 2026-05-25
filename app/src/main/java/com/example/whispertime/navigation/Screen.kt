package com.example.whispertime.navigation

/** 应用导航目标定义，封装每个页面的 route 生成规则。 */
sealed class Screen(val route: String) {
    /** 启动页，根据项目数据跳转到计时页或新建页。 */
    object TimerHome : Screen("timer_home")

    /** 项目新建或编辑页。 */
    data class ProjectEdit(val projectId: Long? = null) : Screen(
        if (projectId != null) "project_edit/$projectId" else "project_edit/new"
    ) {
        /** Compose Navigation 使用的项目编辑 route 模板。 */
        companion object {
            const val ROUTE = "project_edit/{projectId}"
        }
    }

    /** 指定项目的计时页。 */
    data class Timer(val projectId: Long) : Screen("timer/$projectId") {
        /** Compose Navigation 使用的计时页 route 模板。 */
        companion object {
            const val ROUTE = "timer/{projectId}"
        }
    }

    /** 指定项目的历史记录列表页。 */
    data class RecordList(val projectId: Long) : Screen("record_list/$projectId") {
        /** Compose Navigation 使用的记录列表 route 模板。 */
        companion object {
            const val ROUTE = "record_list/{projectId}"
        }
    }

    /** 指定记录的编辑页。 */
    data class RecordEdit(val recordId: Long) : Screen("record_edit/$recordId") {
        /** Compose Navigation 使用的记录编辑 route 模板。 */
        companion object {
            const val ROUTE = "record_edit/{recordId}"
        }
    }
}
