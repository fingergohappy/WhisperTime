# Vibration Reminder Design

**Goal:** Add an optional vibration reminder that follows the existing announcement interval and can be configured on project create/edit screens.

## Scope

- Add a persisted `vibrationEnabled` project setting.
- Expose the toggle on both project create and project edit screens.
- Reuse the existing voice announcement interval for periodic vibration reminders.
- Trigger vibration on prepare countdown ticks, start, interval reminders, and end when the toggle is enabled.

## Architecture

- Extend `ProjectEntity` and `TimerConfig` with a vibration boolean.
- Pass the new field through project editing, timer configuration building, and the foreground timer service.
- Keep timer cadence driven by existing timer and announcement signals so voice and vibration stay aligned.
- Add a small vibration manager to isolate platform vibration APIs from service logic.

## Data Flow

1. User enables vibration on the project create/edit screen.
2. `ProjectEditViewModel` saves the value into `ProjectEntity`.
3. `TimerViewModel` includes the value when building `TimerConfig` and service start arguments.
4. `TimerForegroundService` vibrates at the same lifecycle points where announcements already happen.

## Migration

- Add a Room migration from version 2 to 3.
- New column: `vibrationEnabled INTEGER NOT NULL DEFAULT 0`.
- Avoid destructive migration so existing projects remain intact.

## Testing

- Add a unit test for project edit persistence/loading of `vibrationEnabled`.
- Add a unit test for project-to-timer-config conversion carrying `vibrationEnabled`.
- Run unit tests, then install and launch on the connected device for manual verification.
