package com.example.whispertime.navigation

/**
 * 导航屏幕定义类，封装了所有的路由地址及参数
 */
sealed class Screen(val route: String) {
    /** 项目列表页：首页，展示所有计时项目 */
    object ProjectList : Screen("project_list")
    
    /** 
     * 项目编辑页：创建或修改计时项目
     * @param projectId 项目 ID，为 null 时表示创建新项目
     */
    data class ProjectEdit(val projectId: Long? = null) : Screen(
        if (projectId != null) "project_edit/$projectId" else "project_edit/new"
    ) {
        companion object {
            /** 路由模板，使用 {projectId} 作为占位符 */
            const val ROUTE = "project_edit/{projectId}"
        }
    }
    
    /**
     * 计时工作台：执行具体的计时任务
     * @param projectId 关联的项目 ID
     */
    data class Timer(val projectId: Long) : Screen("timer/$projectId") {
        companion object {
            /** 路由模板，使用 {projectId} 作为占位符 */
            const val ROUTE = "timer/{projectId}"
        }
    }
    
    /**
     * 历史记录列表：查看某个项目的历史计时记录
     * @param projectId 关联的项目 ID
     */
    data class RecordList(val projectId: Long) : Screen("record_list/$projectId") {
        companion object {
            /** 路由模板，使用 {projectId} 作为占位符 */
            const val ROUTE = "record_list/{projectId}"
        }
    }
    
    /**
     * 记录编辑页：修改已完成的计时记录
     * @param recordId 记录 ID
     */
    data class RecordEdit(val recordId: Long) : Screen("record_edit/$recordId") {
        companion object {
            /** 路由模板，使用 {recordId} 作为占位符 */
            const val ROUTE = "record_edit/{recordId}"
        }
    }
}
