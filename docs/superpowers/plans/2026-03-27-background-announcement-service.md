# Background Announcement Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep timer voice announcements running while the app is backgrounded or the screen is off by hardening the existing foreground timer service.

**Architecture:** Persist active timer session data outside process memory, restore timer execution from absolute timestamps when the foreground service is recreated, and hold a partial wake lock only while the timer is actively preparing or running. Keep the current `TimerViewModel -> TimerForegroundService -> TimerEngine -> VoiceAnnouncementManager` structure and thread the new persistence and recovery paths through it.

**Tech Stack:** Kotlin, Android foreground service, SharedPreferences, PowerManager wake locks, JUnit4, kotlinx.coroutines test

---

### Task 1: Add failing tests for session persistence and restoration logic

**Files:**
- Create: `app/src/test/java/com/example/whispertime/service/ActiveTimerSessionStoreTest.kt`
- Create: `app/src/test/java/com/example/whispertime/timer/ActiveTimerSessionResolverTest.kt`
- Modify: `app/src/test/java/com/example/whispertime/timer/TimerEngineTest.kt`

- [ ] Step 1: Write a failing round-trip test for persisted active timer sessions.
- [ ] Step 2: Write failing resolver tests for restoring `PREPARING`, `RUNNING`, `PAUSED`, and completed countdown sessions from absolute timestamps.
- [ ] Step 3: Extend `TimerEngineTest` with a failing restore-path test proving countdown state is recomputed from restored session data.
- [ ] Step 4: Run the focused tests and confirm they fail for missing session persistence and restore support.

### Task 2: Implement active session persistence and restoration primitives

**Files:**
- Create: `app/src/main/java/com/example/whispertime/service/ActiveTimerSession.kt`
- Create: `app/src/main/java/com/example/whispertime/service/ActiveTimerSessionStore.kt`
- Create: `app/src/main/java/com/example/whispertime/timer/ActiveTimerSessionResolver.kt`
- Modify: `app/src/main/java/com/example/whispertime/timer/TimerEngine.kt`
- Modify: `app/src/main/java/com/example/whispertime/di/AppContainer.kt`

- [ ] Step 1: Add the persisted active-session model and store, using `SharedPreferences` with explicit nullable field handling.
- [ ] Step 2: Add a pure resolver that converts stored timestamps into current timer state, elapsed time, remaining time, and completion outcomes.
- [ ] Step 3: Update `TimerEngine` to restore from resolved active-session data and to use elapsed-realtime semantics by default at runtime.
- [ ] Step 4: Re-run the focused tests and confirm the new persistence and restore logic passes.

### Task 3: Harden the foreground service for background execution

**Files:**
- Modify: `app/src/main/java/com/example/whispertime/service/TimerForegroundService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Step 1: Add service-owned session persistence on start, prepare completion, pause, resume, stop, and cancel paths.
- [ ] Step 2: Restore an active session during service creation/start and rebuild foreground execution without resetting elapsed time.
- [ ] Step 3: Add `PARTIAL_WAKE_LOCK` acquisition and release around active preparing/running states, with defensive cleanup in all destroy paths.
- [ ] Step 4: Change the service restart mode to redeliver work and fix the `specialUse` foreground-service subtype declaration placement in the manifest.

### Task 4: Verify the integrated behavior

**Files:**
- Modify: `docs/superpowers/plans/2026-03-27-background-announcement-service.md` only to tick off completed steps if desired

- [ ] Step 1: Run `./gradlew testDebugUnitTest` in the worktree with the explicit local JDK setup.
- [ ] Step 2: Run `./gradlew assembleDebug` to confirm the app still builds after service changes.
- [ ] Step 3: Run `./gradlew installDebug && adb shell monkey -p com.example.whispertime 1` on a connected device for manual verification.
- [ ] Step 4: Manually check background and screen-off announcement continuity on the connected device.
