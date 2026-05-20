package com.example.whispertime.ui.timer.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.whispertime.data.local.entity.ProjectEntity

/** 项目抽屉，用于在计时页内切换项目或创建新项目。 */
@Composable
internal fun ProjectDrawer(
    visible: Boolean,
    projects: List<ProjectEntity>,
    projectId: Long,
    onDismiss: () -> Unit,
    onNavigateToTimer: (Long) -> Unit,
    onNavigateToCreateProject: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180))
    ) {
        BoxScrim(onDismiss = onDismiss)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = tween(260)) { -it } + fadeIn(tween(220)),
        exit = slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut(tween(160))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxSize(),
            shape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("切换项目", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projects, key = { it.id }) { project ->
                        ProjectDrawerItem(
                            project = project,
                            selected = project.id == projectId,
                            onClick = {
                                onDismiss()
                                // 点击当前项目只关闭抽屉，点击其他项目才触发导航。
                                if (project.id != projectId) {
                                    onNavigateToTimer(project.id)
                                }
                            }
                        )
                    }
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onNavigateToCreateProject()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("新建项目")
                }
            }
        }
    }
}

/** 抽屉外层遮罩，点击后关闭项目抽屉。 */
@Composable
private fun BoxScrim(onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss)
    )
}

/** 项目抽屉中的单个项目条目。 */
@Composable
private fun ProjectDrawerItem(
    project: ProjectEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(project.name, fontWeight = FontWeight.Bold)
            Text(
                "${if (selected) "当前项目" else "点击切换"} · ${project.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
