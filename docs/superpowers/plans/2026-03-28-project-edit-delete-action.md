# Project Edit Delete Action Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a delete button to the project settings screen in both create and edit modes, with create-mode exit behavior and edit-mode project deletion.

**Architecture:** Extend `ProjectEditViewModel` with a completion event that distinguishes save from delete-triggered exit, and keep the UI behavior in `ProjectEditScreen` limited to rendering the danger action plus its confirmation dialog. Reuse the existing repository deletion path so edit-screen deletion matches list-screen deletion behavior.

**Tech Stack:** Kotlin, Jetpack Compose, ViewModel, coroutines flow, JUnit4, kotlinx.coroutines test

---

### Task 1: Cover delete behavior with failing ViewModel tests

**Files:**
- Modify: `app/src/test/java/com/example/whispertime/ui/project/ProjectEditViewModelTest.kt`

- [ ] Step 1: Write a failing test proving create-mode delete emits a completion event without creating a project.
- [ ] Step 2: Write a failing test proving edit-mode delete removes the project and emits a completion event.
- [ ] Step 3: Run `./gradlew testDebugUnitTest --tests 'com.example.whispertime.ui.project.ProjectEditViewModelTest'` and confirm the new tests fail for the missing delete behavior.

### Task 2: Implement delete behavior in the edit ViewModel

**Files:**
- Modify: `app/src/main/java/com/example/whispertime/ui/project/ProjectEditViewModel.kt`

- [ ] Step 1: Add a result event that can be emitted by both save and delete flows.
- [ ] Step 2: Implement a `deleteProject()` action that exits immediately in create mode and deletes through the repository in edit mode.
- [ ] Step 3: Re-run `./gradlew testDebugUnitTest --tests 'com.example.whispertime.ui.project.ProjectEditViewModelTest'` and confirm the tests pass.

### Task 3: Add the delete button and confirmation dialog to the settings screen

**Files:**
- Modify: `app/src/main/java/com/example/whispertime/ui/project/ProjectEditScreen.kt`

- [ ] Step 1: Add local screen state for showing the delete confirmation dialog.
- [ ] Step 2: Render the delete button in both modes and vary the dialog copy by mode.
- [ ] Step 3: Hook confirmation to `viewModel.deleteProject()` and keep completion handling routed through the existing back navigation callback.

### Task 4: Verify the integrated change

**Files:**
- Modify: `docs/superpowers/plans/2026-03-28-project-edit-delete-action.md` only to tick off completed steps if desired

- [ ] Step 1: Run `./gradlew testDebugUnitTest`.
- [ ] Step 2: Run `./gradlew assembleDebug`.
- [ ] Step 3: Run `./gradlew installDebug && adb shell monkey -p com.example.whispertime 1` on a connected device.
