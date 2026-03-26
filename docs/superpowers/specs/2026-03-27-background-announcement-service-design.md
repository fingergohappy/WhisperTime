# Background Announcement Service Design

**Goal:** Make timer voice announcements continue while the app is in the background or the screen is off, using the existing foreground-service-based timer design.

## Scope

- Keep the current timer interaction model and announcement interval behavior.
- Make the foreground timer service the sole runtime owner of active timer sessions.
- Hold a `PARTIAL_WAKE_LOCK` only while a timer is actively preparing or running.
- Persist enough timer session state to restore service-driven announcements after service recreation.
- Keep the requirement limited to background and lock-screen use; force-stop or task-swipe survival is out of scope.
- Do not add battery-optimization exemption prompts.

## Architecture

- Extend the timer foreground service to manage session lifecycle independently from UI composition and activity lifecycle.
- Add a small persisted active-session store for the timer service so service recreation can rebuild state from absolute timestamps instead of in-memory counters.
- Keep `TimerEngine` as the timer calculation component, but initialize it from restored session data when needed.
- Add wake-lock management to the service, with clear acquire and release points tied to timer state transitions.
- Fix the foreground service manifest declaration so the special-use subtype is attached to the service entry itself.

## Data Flow

1. User starts a timer from the existing timer screen.
2. `TimerViewModel` sends the current timer configuration to `TimerForegroundService`.
3. The service starts in the foreground, persists the active session, acquires the wake lock, and starts timer execution.
4. The service drives countdown, interval announcements, vibration, and notification updates even if the activity is backgrounded or the screen turns off.
5. If the service is recreated, it reloads the persisted session, rebuilds timer state from stored timestamps, re-enters the foreground, and resumes announcements from the current effective elapsed time.
6. When the timer pauses, stops, or is canceled, the service updates persisted state and releases the wake lock when active execution is no longer needed.

## State Model

- Persisted active session fields should include project id, project name, timer mode, duration, voice interval, vibration enabled, prepare duration, session start wall-clock time, prepare start elapsed-realtime, running start elapsed-realtime, accumulated paused duration, current phase, and last announced interval bucket.
- The persisted phase should distinguish at least `IDLE`, `PREPARING`, `RUNNING`, and `PAUSED`.
- Restoration logic should recompute current elapsed and remaining time from stored absolute timestamps so the timer does not depend on coroutine continuity.

## Error Handling

- If persisted session data is missing or invalid, the service should fail closed by canceling the active session and removing the foreground notification.
- Wake-lock acquisition should be guarded and logged; release should happen in every stop, cancel, pause, and destroy path.
- TTS initialization failures should not crash the timer service; the timer should continue running and keep notification state accurate even if audio output is unavailable.

## Testing

- Add unit coverage for session persistence and restoration calculations across preparing, running, paused, and countdown-complete states.
- Add service-focused tests for wake-lock acquire/release decisions and restoration behavior after simulated recreation.
- Run the relevant unit tests.
- Install and launch on a connected device, then manually verify background and lock-screen announcements with:
  `./gradlew installDebug && adb shell monkey -p com.example.whispertime 1`
