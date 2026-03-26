# Vibration Reminder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist an optional vibration reminder setting per project and trigger device vibration in sync with timer announcements.

**Architecture:** Add a boolean project setting and thread it through project editing, timer config building, and foreground timer execution. Use the existing timer/announcement cadence rather than creating a separate reminder scheduler so vibration stays aligned with current spoken prompts.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Android foreground service, JUnit, coroutines test

---

### Task 1: Document and test the new configuration path

**Files:**
- Modify: `app/src/test/java/com/example/whispertime/timer/TimerEngineTest.kt`
- Create: `app/src/test/java/com/example/whispertime/ui/project/ProjectEditViewModelTest.kt`
- Modify: `app/src/main/java/com/example/whispertime/timer/TimerConfig.kt`
- Modify: `app/src/main/java/com/example/whispertime/ui/timer/TimerViewModel.kt`

- [ ] Step 1: Write failing tests for project vibration persistence and timer config propagation.
- [ ] Step 2: Run the focused tests and confirm they fail for the missing vibration field/path.
- [ ] Step 3: Add the minimal config/model changes to make the tests pass.
- [ ] Step 4: Re-run the focused tests and confirm they pass.

### Task 2: Persist the setting and expose it in project create/edit UI

**Files:**
- Modify: `app/src/main/java/com/example/whispertime/data/local/entity/ProjectEntity.kt`
- Modify: `app/src/main/java/com/example/whispertime/data/local/WhisperTimeDatabase.kt`
- Modify: `app/src/main/java/com/example/whispertime/ui/project/ProjectEditViewModel.kt`
- Modify: `app/src/main/java/com/example/whispertime/ui/project/ProjectEditScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Step 1: Extend the Room entity and migration with `vibrationEnabled`.
- [ ] Step 2: Load/save the value in `ProjectEditViewModel`.
- [ ] Step 3: Add the Compose switch on project create/edit screens.
- [ ] Step 4: Run focused tests after the persistence/UI changes.

### Task 3: Trigger vibration in sync with timer prompts

**Files:**
- Create: `app/src/main/java/com/example/whispertime/vibration/VibrationManager.kt`
- Modify: `app/src/main/java/com/example/whispertime/di/AppContainer.kt`
- Modify: `app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`
- Modify: `app/src/main/java/com/example/whispertime/ui/timer/TimerViewModel.kt`

- [ ] Step 1: Add a vibration manager wrapper for Android vibration APIs.
- [ ] Step 2: Pass the toggle into the foreground service start contract.
- [ ] Step 3: Vibrate on prepare countdown, start, interval reminders, and end when enabled.
- [ ] Step 4: Run the focused tests again, then run the full unit test suite.

### Task 4: Verify on device

**Files:**
- Modify: `app/src/main/res/values/strings.xml` if new string resources are introduced

- [ ] Step 1: Build and run `./gradlew test`.
- [ ] Step 2: Install and launch with `./gradlew installDebug && adb shell monkey -p com.example.whispertime 1`.
- [ ] Step 3: Check create/edit screens and timer vibration behavior on the connected device.
