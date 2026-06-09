## Why

Interview evaluations can fail because of transient LLM, Redis, or provider issues, and users may also want a fresh evaluation after provider or prompt improvements. Today text interview records have no public retry path, and voice interview evaluation returns cached completed results instead of allowing an explicit re-evaluation.

## What Changes

- Add an explicit re-evaluation capability for completed text interview records.
- Add an explicit re-evaluation capability for completed voice interview records.
- Reuse the existing Redis Stream based asynchronous evaluation pipeline for re-evaluation.
- Prevent duplicate re-evaluation jobs while an evaluation is already pending or processing.
- Preserve the existing evaluation report data until a new re-evaluation succeeds, while surfacing failed re-evaluation status and error messages.
- Add frontend actions on the unified interview history page so users can trigger re-evaluation and see existing polling states.

## Capabilities

### New Capabilities
- `interview-reevaluation`: Allows users to trigger asynchronous re-evaluation for eligible text and voice interview records and track the resulting status.

### Modified Capabilities

None.

## Impact

- Backend APIs:
  - Add a text interview re-evaluation endpoint under `/api/interview/sessions/{sessionId}`.
  - Add a voice interview re-evaluation endpoint under `/api/voice-interview/sessions/{sessionId}`.
- Backend services:
  - Add eligibility checks and re-enqueue behavior around existing evaluation stream producers.
  - Ensure voice evaluation persistence updates the existing per-session evaluation row when re-evaluating.
- Frontend:
  - Extend interview API clients.
  - Add re-evaluate actions and loading state to the unified interview history page.
- Tests:
  - Add service/controller coverage for eligibility, duplicate prevention, and re-evaluation overwrite/update behavior.
