# Android 历史重建：空项目 → WhisperTime（逐步提交 + 中文注释）

## TL;DR

> **目标**：在不破坏现有 `tour/main` 的前提下，新建一个 **orphan 分支**，从“Android 空项目（Compose）”开始，用 **约 30–60 个工程师常见粒度的提交**逐步演进到当前 WhisperTime 的功能形态；随后追加若干 **中文注释提交**覆盖每个函数与关键逻辑块。
>
> **交付物**：
> - 一个新分支（示例名：`rebuild-history`），包含可读的渐进式提交历史
> - 每个提交都有清晰 commit message（做了什么 / 为什么 / 下一步）
> - 代码中为函数与关键逻辑块补充中文注释（KDoc 为主）
>
> **关键约束（默认）**：每个提交都应能 `./gradlew :app:assembleDebug`，并保持 `./gradlew test` 通过（不做“红色提交”）。

**Estimated Effort**: Large
**Parallel Execution**: NO（历史重建天然串行）
**Critical Path**: 空项目骨架 → 导航骨架 → TimerEngine+测试 → Room 数据层+测试 → 前台服务 → TTS → CRUD 屏幕 → 对齐 HEAD → 注释补全

---

## Context

### 原始需求（用户）
- 先将项目初始化成安卓的空项目
- 按正常工程师流程一步步恢复到现在的样子
- 每一步都要有 commit，且 message 说明“做了什么/为什么/下一步”
- 在代码中添加中文注释解释每个函数与重要代码块

### 现状（作为目标态参考）
- 单模块 `:app`
- Kotlin + Jetpack Compose + Material3
- Navigation Compose：`app/src/main/java/com/example/whispertime/navigation/WhisperTimeNavHost.kt`
- 手写 DI：`app/src/main/java/com/example/whispertime/di/AppContainer.kt` + `app/src/main/java/com/example/whispertime/WhisperTimeApplication.kt`
- Room：`app/src/main/java/com/example/whispertime/data/local/WhisperTimeDatabase.kt` 等
- TimerEngine（Flow+协程）：`app/src/main/java/com/example/whispertime/timer/TimerEngine.kt` + 测试 `app/src/test/java/com/example/whispertime/timer/TimerEngineTest.kt`
- 前台服务：`app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`
- TTS：`app/src/main/java/com/example/whispertime/tts/VoiceAnnouncementManager.kt`
- 参考里程碑 tags：`learn-00-original`..`learn-08-polish`

---

## Work Objectives

### 核心目标
1) 用 orphan 分支重建一条“合理的开发历史”，逐步产出与当前实现一致的功能（项目管理/计时器/记录/TTS/前台服务）。
2) 在不改变行为的前提下，补齐中文注释，解释每个函数与关键逻辑块。

### Scope
- IN:
  - Gradle/版本目录/构建脚本（仅为支撑历史演进与稳定构建）
  - `:app` 的 UI、导航、ViewModel、数据层（Room）、Service、TTS、timer domain
  - 单元测试（优先覆盖纯逻辑与 Repository 层）
  - 中文注释（KDoc + 必要的块注释）
- OUT:
  - 新增产品功能、UI 重新设计、重命名包名/模块化拆分、引入大型 DI 框架（Hilt/Koin）
  - 提交任何生成物（`build/`, `ksp/`, Gradle caches）。

### Guardrails
- 只在新 orphan 分支上操作；不重写 `tour/main`，不移动现有 tags。
- 每个提交保持可构建（默认还要 `./gradlew test` 通过）。
- TDD 采用“提交保持绿色”的实践：测试与最小实现可在同一 commit 内落地（本地开发可先红后绿）。
- Room schema JSON 是否纳入版本控制需明确（默认：不提交导出 schema，保持 `exportSchema=false` 的现状）。

---

## Verification Strategy（全程可由执行代理完成，无需人工操作）

### 基础命令（每个提交至少满足）
- `./gradlew :app:assembleDebug`

### 单元测试（推荐每个提交都跑；至少在逻辑/数据层提交必须跑）
- `./gradlew test`

### 里程碑加严（每到一个 learn 里程碑末尾）
- `./gradlew :app:lintDebug`（如果耗时可降频）
- `./gradlew :app:assembleRelease`（捕获 manifest/混淆/资源问题）

### 终局一致性校验
- 在“功能对齐提交”（注释前）做一次严格对比：与原分支目标 HEAD 的 `git diff --exit-code`（执行代理完成）
- 注释阶段：确保差异仅为注释与换行（并保持 build + tests 通过）

---

## Execution Setup（非提交步骤）

1) 从现有仓库创建 orphan 分支（示例）：`rebuild-history`
2) 保留一个“参考指针”到原目标分支/commit（例如 `tour` 当前 HEAD），后续用于 diff 校验
3) 规划里程碑与提交序列（本计划给出推荐拆分）

---

## Commit Message 规范（强制）

每个 commit：
- Title：`type(scope): 一句话说明变更`
- Body（中文）：
  - `为什么：...`（动机/约束/权衡）
  - `下一步：...`（可为空，但尽量写明确的下一步）

type 建议：`chore|build|feat|refactor|test|docs`

---

## TODOs（每条 = 1 个 commit；约 40–55 个）

说明：
- “参考文件”全部指向当前目标态（原分支上的同名文件），用于对照实现。
- 每条 TODO 的验收均为执行代理可运行的命令（build/test/diff）。

### Milestone A：空项目骨架（Compose）

- [x] 1. `build(init): 创建 Compose 空项目骨架（可编译）`

  **要做什么**：
  - 建立 Gradle Wrapper、`settings.gradle.kts`、根 `build.gradle.kts`、`gradle/libs.versions.toml`、`gradle.properties`
  - 建立 `:app` 模块最小可编译结构（manifest、MainActivity、主题与资源）

  **参考文件**：
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `app/build.gradle.kts`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/example/whispertime/MainActivity.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [x] 2. `chore(app): 添加 Application 与手写 DI 容器（未接入业务）`

   **要做什么**：
   - 新增 `WhisperTimeApplication` 与 `AppContainer`，先只放最小字段/占位
   - 在 manifest 配置 `android:name`

   **参考文件**：
   - `app/src/main/java/com/example/whispertime/WhisperTimeApplication.kt`
   - `app/src/main/java/com/example/whispertime/di/AppContainer.kt`
   - `app/src/main/AndroidManifest.xml`

   **验收**：
   - `./gradlew :app:assembleDebug` ✓

### Milestone B：导航骨架（learn-02 对齐前置）

- [x] 3. `feat(nav): 引入 Screen 路由模型与 NavHost 框架`

  **要做什么**：
  - 新增 `Screen` sealed/route 定义
  - 新增 `WhisperTimeNavHost`，先用占位 screen
  - `MainActivity` 切换到 NavHost

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/navigation/Screen.kt`
  - `app/src/main/java/com/example/whispertime/navigation/WhisperTimeNavHost.kt`
  - `app/src/main/java/com/example/whispertime/MainActivity.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [x] 4. `feat(ui): 添加项目列表占位页（导航可达）`

  **要做什么**：
  - 新增 `ProjectListScreen`（先展示静态 UI/按钮）
  - 在 NavHost 设为 startDestination

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/project/ProjectListScreen.kt`
  - `app/src/main/java/com/example/whispertime/navigation/WhisperTimeNavHost.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

### Milestone C：TimerEngine（纯逻辑 + 单测；learn-03）

- [x] 5. `feat(timer): 定义 Timer 领域模型（状态/配置/结果）`

  **要做什么**：
  - 新增 `TimerState`、`TimerConfig`、`TimerResult`、`TimerMode`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/timer/TimerState.kt`
  - `app/src/main/java/com/example/whispertime/timer/TimerConfig.kt`
  - `app/src/main/java/com/example/whispertime/timer/TimerResult.kt`
  - 说明：`TimerMode` 在目标态中定义于 `app/src/main/java/com/example/whispertime/timer/TimerConfig.kt`

  **验收**：
  - `./gradlew test`

- [x] 6. `test(timer): 添加 TimerEngine 行为测试（先覆盖计时增长/倒计时）`

  **要做什么**：
  - 新增 `TimerEngineTest`，覆盖：count-up elapsed 增长、countdown remaining 下降
  - 保持 commit 绿色：同一 commit 内可同时落地最小实现（见下一条）

  **参考文件**：
  - `app/src/test/java/com/example/whispertime/timer/TimerEngineTest.kt`

  **验收**：
  - `./gradlew test`

- [x] 7. `feat(timer): 实现 TimerEngine（Flow 状态、pause/resume/stop）`

  **要做什么**：
  - 实现 `TimerEngine`（state/elapsed/remaining/prepareRemaining/shouldAnnounce）
  - 补齐测试：pause 冻结、stop 返回结果、倒计时归零自动停止

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/timer/TimerEngine.kt`
  - `app/src/test/java/com/example/whispertime/timer/TimerEngineTest.kt`

  **验收**：
  - `./gradlew test`

- [x] 8. `feat(timer): 增加语音间隔触发信号（shouldAnnounce）`

  **要做什么**：
  - `TimerEngine` 增加 voiceInterval 触发 `shouldAnnounce`
  - 测试覆盖触发频率（参考现有 `voiceAnnouncement_triggersAtInterval`）

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/timer/TimerEngine.kt`
  - `app/src/test/java/com/example/whispertime/timer/TimerEngineTest.kt`

  **验收**：
  - `./gradlew test`

### Milestone D：Room 数据层（learn-04）

- [x] 9. `build(room): 引入 Room + KSP 依赖与 schema 配置`

  **要做什么**：
  - 在 `app/build.gradle.kts` 增加 Room runtime/ktx/compiler(KSP)
  - 配置 ksp arg（若目标态已有）

  **参考文件**：
  - `app/build.gradle.kts`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [x] 10. `feat(data): 添加 ProjectEntity/Dao/Repository（最小可用）`

  **要做什么**：
  - 新增 entity + dao
  - 新增 repository（Flow/CRUD）
  - 在 `AppContainer` 先接入 dao/repo（如需）

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/data/local/entity/ProjectEntity.kt`
  - `app/src/main/java/com/example/whispertime/data/local/dao/ProjectDao.kt`
  - `app/src/main/java/com/example/whispertime/data/repository/ProjectRepository.kt`
  - `app/src/main/java/com/example/whispertime/di/AppContainer.kt`

  **验收**：
  - `./gradlew test`

- [x] 11. `feat(data): 添加 TimingRecordEntity/Dao/Repository（最小可用）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/data/local/entity/TimingRecordEntity.kt`
  - `app/src/main/java/com/example/whispertime/data/local/dao/TimingRecordDao.kt`
  - `app/src/main/java/com/example/whispertime/data/repository/TimingRecordRepository.kt`

  **验收**：
  - `./gradlew test`

- [x] 12. `feat(db): 引入 WhisperTimeDatabase 并在 AppContainer 初始化`

  **要做什么**：
  - 新增 `WhisperTimeDatabase`（RoomDatabase + getInstance）
  - 在 `AppContainer` 创建 database 并暴露 dao

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/data/local/WhisperTimeDatabase.kt`
  - `app/src/main/java/com/example/whispertime/di/AppContainer.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [x] 13. `test(data): 为 Repository 增加基础单测（插入/查询/删除）`

  **参考文件**：
  - `app/src/test/java/com/example/whispertime/repository/TimingRecordRepositoryTest.kt`
  - 说明：目标态目前可见测试以 TimingRecordRepository 为主；若新增 ProjectRepository 测试，应保持相同风格与运行方式。

  **验收**：
  - `./gradlew test`

### Milestone E：项目管理 UI（CRUD）（learn-07 前半）

- [x] 14. `feat(project): ProjectListViewModel 接入 Repository（列表流）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/project/ProjectListViewModel.kt`
  - `app/src/main/java/com/example/whispertime/data/repository/ProjectRepository.kt`
  - `app/src/main/java/com/example/whispertime/di/AppContainer.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [x] 15. `feat(project): ProjectListScreen 显示真实数据 + 跳转入口`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/project/ProjectListScreen.kt`
  - `app/src/main/java/com/example/whispertime/navigation/WhisperTimeNavHost.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [x] 16. `feat(project): 添加 ProjectEditScreen + ViewModel（创建/编辑）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/project/ProjectEditScreen.kt`
  - `app/src/main/java/com/example/whispertime/ui/project/ProjectEditViewModel.kt`
  - `app/src/main/java/com/example/whispertime/navigation/WhisperTimeNavHost.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

### Milestone F：计时 UI + ViewModel（连接 TimerEngine）（learn-03→learn-05 过渡）

- [x] 17. `feat(timer-ui): TimerViewModel 引入 TimerEngine 状态流`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/timer/TimerViewModel.kt`
  - `app/src/main/java/com/example/whispertime/timer/TimerEngine.kt`

  **验收**：
  - `./gradlew test`

- [x] 18. `feat(timer-ui): TimerScreen UI（模式/时长/间隔/控制）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/timer/TimerScreen.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

### Milestone G：前台服务（learn-05）

- [x] 19. `feat(service): 添加 TimerForegroundService 骨架 + NotificationChannel`

  **要做什么**：
  - 新增服务类，最小可编译
  - 增加 channel 创建、基本 notification 构建

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`
  - `app/src/main/AndroidManifest.xml`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [x] 20. `feat(service): 接入 ACTION_START/PAUSE/RESUME/STOP/CANCEL 及 PendingIntent`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 21. `chore(manifest): 补齐前台服务权限与 foregroundServiceType 配置`

  **要做什么**：
  - `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `POST_NOTIFICATIONS`
  - `<service android:foregroundServiceType="specialUse" ...>`
  - `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ...>`

  **参考文件**：
  - `app/src/main/AndroidManifest.xml`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 22. `feat(service): 服务观察 TimerEngine 完成信号并落库 TimingRecord`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`
  - `app/src/main/java/com/example/whispertime/data/repository/TimingRecordRepository.kt`
  - `app/src/main/java/com/example/whispertime/data/local/entity/TimingRecordEntity.kt`

  **验收**：
  - `./gradlew test`

### Milestone H：TTS（learn-06）

- [ ] 23. `feat(tts): 引入 VoiceAnnouncementManager（队列 + 就绪状态）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/tts/VoiceAnnouncementManager.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 24. `feat(di): AppContainer 初始化 VoiceAnnouncementManager 并 init`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/di/AppContainer.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 25. `feat(service): 服务中接入语音播报（prepare 倒计时 + 间隔播报 + stopSpeaking）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`
  - `app/src/main/java/com/example/whispertime/tts/VoiceAnnouncementManager.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

### Milestone I：记录列表/编辑（CRUD）（learn-07 后半）

- [ ] 26. `feat(record): RecordListViewModel 接入 TimingRecordRepository`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/record/RecordListViewModel.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 27. `feat(record): RecordListScreen 展示记录 + 跳转编辑`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/record/RecordListScreen.kt`
  - `app/src/main/java/com/example/whispertime/navigation/WhisperTimeNavHost.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 28. `feat(record): RecordEditViewModel + Screen（手动修正时长/起止时间）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/record/RecordEditViewModel.kt`
  - `app/src/main/java/com/example/whispertime/ui/record/RecordEditScreen.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

### Milestone J：端到端串联与对齐目标态（learn-08 前）

- [ ] 29. `feat(nav): 完整串联所有路由与参数（projectId/recordId）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/navigation/WhisperTimeNavHost.kt`
  - `app/src/main/java/com/example/whispertime/navigation/Screen.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 30. `feat(timer-ui): TimerScreen 通过 Intent 启动/控制前台服务`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/ui/timer/TimerViewModel.kt`
  - `app/src/main/java/com/example/whispertime/ui/timer/TimerScreen.kt`
  - `app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 31. `chore(res): 补齐备份规则/strings/themes/colors 与图标资源对齐`

  **参考文件**：
  - `app/src/main/res/xml/backup_rules.xml`
  - `app/src/main/res/xml/data_extraction_rules.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/mipmap-*/ic_launcher*.webp`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 32. `chore(polish): 对齐目标态的细节（日志/边界条件/小修正）`

  **要做什么**：
  - 以当前目标态代码为准，补齐遗漏字段/参数/默认值
  - 保持行为不变（只为对齐）

  **参考文件**：
  - `design.md`（功能清单）
  - 全量对照：`app/src/main/java/com/example/whispertime/` 下现有文件

  **验收**：
  - `./gradlew test`
  - `./gradlew :app:assembleDebug`

- [ ] 33. `chore(sync): 功能态最终对齐（与原 HEAD 做严格 diff 校验）`

  **要做什么**：
  - 确保此提交结束后（不含注释），与原分支目标 HEAD 在功能代码层面一致
  - 若存在差异，必须在此 commit 内消除（不把差异带入注释阶段）

  **验收**：
  - `./gradlew test`
  - `./gradlew :app:assembleDebug`
  - `git diff --exit-code tour..HEAD`（以现有 `tour` 分支 HEAD 作为“目标态参考”）

### Milestone K：中文注释补全（comment-only commits；不改行为）

注释规范（建议）：
- 对每个类/函数用 KDoc（`/** ... */`）写中文说明：作用、关键逻辑、边界条件、线程/协程语义。
- 对复杂逻辑块用块注释解释“为什么这样做”。
- 不写复述型注释（例如 `// 设置 state` 这种无信息注释）。

- [ ] 34. `docs(comment): 为 timer 包补齐中文注释（不改逻辑）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/timer/TimerEngine.kt`
  - `app/src/main/java/com/example/whispertime/timer/TimerConfig.kt`
  - `app/src/main/java/com/example/whispertime/timer/TimerState.kt`
  - `app/src/main/java/com/example/whispertime/timer/TimerResult.kt`

  **验收**：
  - `./gradlew test`

- [ ] 35. `docs(comment): 为 data/di 包补齐中文注释（Room/Repository/DI）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/data/local/WhisperTimeDatabase.kt`
  - `app/src/main/java/com/example/whispertime/data/local/dao/*.kt`
  - `app/src/main/java/com/example/whispertime/data/local/entity/*.kt`
  - `app/src/main/java/com/example/whispertime/data/repository/*.kt`
  - `app/src/main/java/com/example/whispertime/di/AppContainer.kt`

  **验收**：
  - `./gradlew test`

- [ ] 36. `docs(comment): 为 navigation/ui 包补齐中文注释（路由/事件/状态）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/navigation/Screen.kt`
  - `app/src/main/java/com/example/whispertime/navigation/WhisperTimeNavHost.kt`
  - `app/src/main/java/com/example/whispertime/ui/**`（Project/Record/Timer screens + VMs）

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 37. `docs(comment): 为 service/tts/入口类补齐中文注释（生命周期与边界）`

  **参考文件**：
  - `app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`
  - `app/src/main/java/com/example/whispertime/tts/VoiceAnnouncementManager.kt`
  - `app/src/main/java/com/example/whispertime/MainActivity.kt`
  - `app/src/main/java/com/example/whispertime/WhisperTimeApplication.kt`

  **验收**：
  - `./gradlew :app:assembleDebug`

- [ ] 38. `chore(comment): 注释一致性与格式清理（仅注释/空行）`

  **要做什么**：
  - 统一术语（项目/计时/记录/倒计时/正计时）
  - 避免重复/空洞注释

  **验收**：
  - `./gradlew test`

---

## Success Criteria

- 新分支 `rebuild-history` 存在，且提交数约 30–60（功能阶段）+ 若干注释提交
- 每个提交 message 满足 what/why/next 约定
- 每个提交可 `./gradlew :app:assembleDebug`，并保持 `./gradlew test` 通过（至少逻辑/数据层提交必须）
- 在“功能对齐提交”（第 33 步）与原目标 HEAD 的 `git diff --exit-code` 为 0
- 注释阶段不引入行为变化，所有测试与构建仍通过
