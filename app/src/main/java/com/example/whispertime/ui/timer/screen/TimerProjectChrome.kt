package com.example.whispertime.ui.timer.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 计时页上方的小型数字设置输入框。 */
@Composable
internal fun SmallSettingField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                decorationBox = { innerTextField ->
                    innerTextField()
                }
            )
        }
    }
}

/** 相邻项目提示，用于提示左右滑动切换目标。 */
@Composable
internal fun NeighborProjectHint(
    modifier: Modifier = Modifier,
    title: String,
    projectName: String?,
    alignEnd: Boolean
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontWeight = FontWeight.Light
        )
        Text(
            text = projectName ?: "无",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
        )
    }
}

/**
 * 计时页顶部项目区域，集中展示当前项目标题、抽屉入口、设置入口和相邻项目提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerProjectHeader(
    projectName: String,
    showNeighborHints: Boolean,
    previousProjectName: String?,
    nextProjectName: String?,
    canOpenSettings: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CenterAlignedTopAppBar(
            title = {
                AnimatedContent(
                    targetState = projectName.ifBlank { "WHISPER TIME" },
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)).togetherWith(
                            fadeOut(animationSpec = tween(200))
                        )
                    },
                    label = "project_title"
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Open drawer",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { if (canOpenSettings) onOpenSettings() },
                    enabled = canOpenSettings
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        if (showNeighborHints) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeighborProjectHint(
                    modifier = Modifier.weight(1f),
                    title = "上一个",
                    projectName = previousProjectName,
                    alignEnd = false
                )
                
                // 项目指示器小点
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == 1) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == 1) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                NeighborProjectHint(
                    modifier = Modifier.weight(1f),
                    title = "下一个",
                    projectName = nextProjectName,
                    alignEnd = true
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 无项目时展示的空状态，引导用户先创建项目。 */
@Composable
internal fun EmptyProjectState(
    modifier: Modifier = Modifier,
    onNavigateToCreateProject: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "开启你的专注旅程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "通过创建项目来规划你的时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onNavigateToCreateProject,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("新建项目", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
