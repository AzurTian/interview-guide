## Context

The platform has two interview modalities that already use asynchronous evaluation:

- Text interviews store reports on `InterviewSessionEntity` plus per-question updates on `InterviewAnswerEntity`.
- Voice interviews store evaluation results in `VoiceInterviewEvaluationEntity` and status on `VoiceInterviewSessionEntity`.

Both modalities use Redis Stream producers and consumers with `AsyncTaskStatus` values
`PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED`. The unified interview history page already lists text and voice records together and polls while evaluation is pending or processing.

Current gaps:

- Text interviews have no public endpoint for re-enqueueing evaluation after completion or failure.
- Voice interviews expose `POST /api/voice-interview/sessions/{sessionId}/evaluation`, but that endpoint returns cached results when evaluation is already completed.
- Voice evaluation persistence creates a new evaluation entity, which is not compatible with re-evaluation because `session_id` is unique.

## Goals / Non-Goals

**Goals:**

- Provide explicit re-evaluation endpoints for both text and voice interview records.
- Reuse the existing Redis Stream evaluation infrastructure.
- Keep old report data visible until a re-evaluation succeeds.
- Prevent duplicate evaluation jobs for records already in `PENDING` or `PROCESSING`.
- Let the existing frontend polling flow reflect re-evaluation progress.

**Non-Goals:**

- Do not introduce evaluation version history.
- Do not allow users to edit answers before re-evaluation.
- Do not change the scoring model or prompt format.
- Do not replace Redis Stream task processing.
- Do not make synchronous LLM calls from the re-evaluation endpoints.

## Decisions

### Use explicit `reevaluate` endpoints

Add dedicated endpoints:

- `POST /api/interview/sessions/{sessionId}/reevaluate`
- `POST /api/voice-interview/sessions/{sessionId}/reevaluate`

Rationale: voice `POST /evaluation` already means "generate if needed or return current status." Reusing it for forced re-evaluation would make the existing endpoint ambiguous and could surprise pages that call it only to initialize polling.

Alternative considered: add a `force=true` query parameter to existing evaluation endpoints. This keeps fewer routes but makes client behavior less obvious and requires different semantics on a previously idempotent-looking endpoint.

### Re-evaluation overwrites the current report

The system will keep one current report per interview record. A successful re-evaluation overwrites the prior score, feedback, strengths, improvements, reference answers, and per-question evaluation details.

Rationale: the existing data model represents a current report, not a versioned report history. Versioning would require schema, UI, export, and comparison behavior that is not needed for retry/fresh-evaluation use cases.

Alternative considered: store every re-evaluation attempt as a version. This is useful for auditability but materially expands scope.

### Preserve old report data during failed re-evaluation

When a user triggers re-evaluation, only `evaluateStatus` and `evaluateError` change immediately. Existing report fields remain available until the new task succeeds. If the task fails, the record shows `FAILED` with an error while retaining the last successful report data.

Rationale: deleting or clearing the previous report would degrade the user experience and make transient provider failures destructive.

### Gate by interview completion and evaluation status

Re-evaluation is allowed only for records that are complete enough to evaluate:

- Text status: `COMPLETED` or `EVALUATED`
- Voice status: `COMPLETED`

Re-evaluation is rejected or returned unchanged when `evaluateStatus` is `PENDING` or `PROCESSING`.

Rationale: evaluating in-progress interviews can produce partial or misleading reports. Duplicate stream jobs can race and overwrite each other.

### Update existing voice evaluation rows

Voice re-evaluation must upsert by `sessionId`: update an existing `VoiceInterviewEvaluationEntity` when present, or create one when absent.

Rationale: `voice_interview_evaluations.session_id` is unique, so inserting a new row for a re-evaluated session would fail.

## Risks / Trade-offs

- Duplicate requests can still race between the eligibility check and status update -> keep the status update and enqueue path in one transactional service method where possible; rely on status gating to reduce duplicate work.
- Re-evaluation failure leaves a record with old report data and `FAILED` status -> frontend should communicate the status clearly and still allow another retry.
- Voice history list currently does not expose `overallScore` -> showing voice scores on the unified list is a separate enhancement; re-evaluation can still be triggered and observed through status.
- Redis enqueue can fail after status is set to `PENDING` -> existing producer failure handling must mark the record `FAILED` with a truncated error.
