package com.example.whispertime.ui.timer.screen

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 计时页主内容模式。 */
internal enum class TimerViewMode {
    /** 计时主页面。 */
    TIMER,

    /** 项目设置页面。 */
    SETTINGS
}

/** 底部面板标签页。 */
internal enum class BottomTab {
    /** 历史记录标签页。 */
    HISTORY,

    /** 统计数据标签页。 */
    STATS
}

/** 底部面板展开状态。 */
internal enum class BottomPanelState {
    /** 仅显示拖拽把手。 */
    COLLAPSED,

    /** 半屏展示。 */
    HALF,

    /** 近似全屏展示。 */
    EXPANDED
}

/** 底部面板统一圆角形状。 */
internal val PanelShape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp)
