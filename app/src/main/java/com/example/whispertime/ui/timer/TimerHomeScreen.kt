package com.example.whispertime.ui.timer

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whispertime.ui.project.ProjectListViewModel

@Composable
fun TimerHomeScreen(
    onNavigateToTimer: (Long) -> Unit,
    onNavigateToCreateProject: () -> Unit,
    projectListViewModel: ProjectListViewModel = viewModel(
        factory = ProjectListViewModel.factory(LocalContext.current.applicationContext as Application)
    )
) {
    val projects by projectListViewModel.projects.collectAsState()

    if (projects.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                Button(onClick = onNavigateToCreateProject) {
                    Text("暂无项目，去新建项目")
                }
            }
        }
        return
    }

    val firstProject = projects.first()
    LaunchedEffect(firstProject.id) {
        onNavigateToTimer(firstProject.id)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在进入计时页…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
