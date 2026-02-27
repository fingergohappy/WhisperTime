- 2026-02-27: 任务 18 发现 `TimerScreen` 源码已完整，决定仅在 `WhisperTimeNavHost` 接入路由并传递 `projectId`，无需额外修改 UI 组件即可满足需求。
- 2026-02-27: 任务 19 决定在 Manifest 仅声明 `TimerForegroundService`（`exported=false`），前台服务权限与 `foregroundServiceType` 延后到任务 21 统一处理。
- 2026-02-27: 任务 20 决定仅在 `TimerForegroundService` 增量接入 ACTION 分发与通知按钮，不提前引入任务 21 权限配置与任务 22 完成信号落库。
- 2026-02-27: 任务 21 决定将 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 的值落在 `strings.xml`（`timer_service_fgs_subtype`），避免在 Manifest 内硬编码，便于后续统一文案与配置管理。
- 2026-02-27: 任务 22 决定由 `TimerForegroundService` 独占消费 `TimerEngine.shouldAnnounce == -1L` 完成事件并写入 `TimingRecordRepository`，`TimerViewModel` 不再执行完成后落库，避免双写。
- 2026-02-27: 任务 23 决定 `VoiceAnnouncementManager` 仅实现管理器内部能力（初始化状态、队列播报、`stopSpeaking`、`release`），不提前改动 `AppContainer` 与 `TimerForegroundService` 接线。
- 2026-02-27: 任务 24 决定在 `AppContainer` 新增 `clear()` 仅负责 `voiceAnnouncementManager.release()`，并通过 `WhisperTimeApplication.onTerminate()` 挂入最小释放路径，不提前触碰 service 播报分支（任务 25）。
- 2026-02-27: 任务 25 决定在 `TimerForegroundService` 内严格分流 `shouldAnnounce`：`-1L` 仅用于完成落库与收尾，`>0` 才触发间隔播报，避免回退任务 22 的单点落库语义。
- 2026-02-27: 任务 25 决定在 `ACTION_STOP`/`ACTION_CANCEL`/`onDestroy` 统一调用 `voiceAnnouncementManager.stopSpeaking()`，并在新会话 `ACTION_START` 前先清空残留播报队列。
- 2026-02-27: 任务 26 决定保持 UI 与导航不变，仅在 `RecordListViewModel` 接入按项目过滤的 repository 流，并保留最小删除与统计入口供任务 27 复用。
- 2026-02-27: 任务 29 决定统一采用语义化参数名（`projectId`、`recordId`），并在 `Screen.createRoute`、`navArgument`、`backStackEntry.arguments` 三处保持同名映射，避免 `id` 多义性。
- 2026-02-27: 任务 29 决定保留 `ProjectEdit` 的可空参数以支持新建/编辑复用，同时将 `RecordEdit` 固化为必填 `recordId`，并对参数缺失场景执行 `popBackStack()` 保护而非崩溃。
- 2026-02-27: 任务 30 决定采用“验证型收敛”，不改动 `TimerViewModel/TimerScreen/TimerForegroundService` 业务代码；当前 Intent 控制链（start/pause/resume/stop/cancel）与 action/extra 协议已满足目标。
- 2026-02-27: 任务 30 决定仅更新计划勾选与 notepad 记录，避免提前触碰任务 31 的资源/主题对齐范围。

## Task 27: RecordListScreen and Navigation
- **Navigation Type for RecordEdit**: Used `String` type for `id` argument in `RecordEdit` route to support potential nullable IDs (for create vs edit), although for this specific task we are navigating to *edit* an existing record, so the ID is available. However, keeping it consistent with  (which supports create/edit) is a good practice.
- **Placeholder for RecordEdit**: Created a placeholder for `RecordEditScreen` in `WhisperTimeNavHost` as the actual screen implementation is in Task 28.
- 2026-02-27: 任务 32 决定仅做 behavior-preserving 的细节抛光（参数 guard/日志补齐），不引入任何 task 33 的 diff-sync 改动。

## Task 27: RecordListScreen and Navigation
- **Navigation Type for RecordEdit**: Used `String` type for `id` argument in `RecordEdit` route to support potential nullable IDs (for create vs edit), although for this specific task we are navigating to *edit* an existing record, so the ID is available. However, keeping it consistent with `ProjectEdit` (which supports create/edit) is a good practice.
- **Placeholder for RecordEdit**: Created a placeholder for `RecordEditScreen` in `WhisperTimeNavHost` as the actual screen implementation is in Task 28.
