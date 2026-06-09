## 1. Text Interview Backend

- [x] 1.1 Add a service method that validates text interview re-evaluation eligibility, returns current status for `PENDING` or `PROCESSING`, and rejects incomplete sessions with `BusinessException`.
- [x] 1.2 In the text re-evaluation service flow, set `evaluateStatus` to `PENDING`, clear `evaluateError`, and enqueue the existing text evaluation Redis Stream task.
- [x] 1.3 Add `POST /api/interview/sessions/{sessionId}/reevaluate` returning a lightweight status response without invoking LLM synchronously.

## 2. Voice Interview Backend

- [x] 2.1 Add a service method that validates voice interview re-evaluation eligibility, returns current status for `PENDING` or `PROCESSING`, and rejects incomplete sessions with `BusinessException`.
- [x] 2.2 Add `POST /api/voice-interview/sessions/{sessionId}/reevaluate` returning the updated voice evaluation status.
- [x] 2.3 Change voice evaluation persistence to update the existing `VoiceInterviewEvaluationEntity` for a session when present, or create one when absent.

## 3. Frontend Integration

- [x] 3.1 Add text and voice re-evaluation API client functions.
- [x] 3.2 Add re-evaluation actions to the unified interview history table for eligible text and voice records.
- [x] 3.3 Disable or hide the re-evaluation action while a record is `PENDING` or `PROCESSING`, and refresh the list after triggering re-evaluation.

## 4. Verification

- [x] 4.1 Add or update backend tests for text re-evaluation eligibility, duplicate prevention, and enqueue behavior.
- [x] 4.2 Add or update backend tests for voice re-evaluation eligibility, duplicate prevention, and evaluation row update behavior.
- [x] 4.3 Run relevant backend tests and frontend type/build checks.
- [x] 4.4 Run `openspec status --change add-interview-reevaluation` and confirm the change is apply-ready.
