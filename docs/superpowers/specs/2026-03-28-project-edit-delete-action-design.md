# Project Edit Delete Action Design

**Goal:** Add a delete action to the project settings screen for both create and edit modes, with mode-specific confirmation behavior.

## Scope

- Show a delete button on the project settings screen in both create and edit modes.
- In create mode, confirm deletion and then simply leave the screen without persisting anything.
- In edit mode, confirm deletion, delete the project, cascade-delete linked timing records, and then leave the screen.
- Keep the existing project list deletion behavior unchanged.

## Architecture

- Add a delete action and delete-result event to `ProjectEditViewModel` so the screen can react without duplicating persistence logic.
- Keep the UI change isolated to `ProjectEditScreen`, adding one danger-action button and one confirmation dialog whose copy depends on mode.
- Reuse the existing `ProjectRepository.deleteProject()` path so deletion behavior stays consistent with project-list deletion.

## Data Flow

1. User opens the project settings screen.
2. The screen always shows a delete button near the bottom.
3. Tapping delete opens a confirmation dialog.
4. If the screen is in create mode, confirming emits a delete/exit result and navigates back.
5. If the screen is in edit mode, confirming deletes the project through the repository, then emits the result and navigates back.

## Error Handling

- If the project is missing in edit mode when delete is confirmed, do not crash; emit the same completion result and leave the screen.
- Do not allow the delete button to save partial form state in create mode.

## Testing

- Add ViewModel tests for create-mode delete and edit-mode delete.
- Run focused tests for `ProjectEditViewModel`.
- Run the full debug unit test suite and a debug assemble.
- Attempt install and launch on a connected device with the project-standard command.
