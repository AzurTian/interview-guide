## ADDED Requirements

### Requirement: Text interview records can be re-evaluated
The system SHALL allow users to trigger asynchronous re-evaluation for a text interview record whose session status is `COMPLETED` or `EVALUATED`.

#### Scenario: Re-evaluate completed text interview
- **WHEN** a user requests re-evaluation for a text interview session with status `COMPLETED` or `EVALUATED` and no evaluation currently pending or processing
- **THEN** the system SHALL set the session evaluation status to `PENDING`, clear the evaluation error, enqueue a text interview evaluation task, and return the updated evaluation status

#### Scenario: Reject incomplete text interview
- **WHEN** a user requests re-evaluation for a text interview session with status `CREATED` or `IN_PROGRESS`
- **THEN** the system SHALL reject the request with a business error indicating the interview is not completed

### Requirement: Voice interview records can be re-evaluated
The system SHALL allow users to trigger asynchronous re-evaluation for a voice interview record whose session status is `COMPLETED`.

#### Scenario: Re-evaluate completed voice interview
- **WHEN** a user requests re-evaluation for a voice interview session with status `COMPLETED` and no evaluation currently pending or processing
- **THEN** the system SHALL set the session evaluation status to `PENDING`, clear the evaluation error, enqueue a voice interview evaluation task, and return the updated evaluation status

#### Scenario: Reject incomplete voice interview
- **WHEN** a user requests re-evaluation for a voice interview session that is not completed
- **THEN** the system SHALL reject the request with a business error indicating the interview is not completed

### Requirement: Re-evaluation jobs are not duplicated while active
The system SHALL prevent duplicate re-evaluation jobs for a record whose evaluation status is already `PENDING` or `PROCESSING`.

#### Scenario: Re-evaluation already queued
- **WHEN** a user requests re-evaluation for an interview record whose evaluation status is `PENDING`
- **THEN** the system SHALL not enqueue another task and SHALL return the current `PENDING` status

#### Scenario: Re-evaluation already processing
- **WHEN** a user requests re-evaluation for an interview record whose evaluation status is `PROCESSING`
- **THEN** the system SHALL not enqueue another task and SHALL return the current `PROCESSING` status

### Requirement: Successful re-evaluation replaces the current report
The system SHALL replace the current evaluation report with the newly generated result when re-evaluation succeeds.

#### Scenario: Text re-evaluation succeeds
- **WHEN** a text interview re-evaluation task completes successfully
- **THEN** the system SHALL update the session score, overall feedback, strengths, improvements, reference answers, per-question scores, per-question feedback, and evaluation status to `COMPLETED`

#### Scenario: Voice re-evaluation succeeds
- **WHEN** a voice interview re-evaluation task completes successfully for a session with an existing evaluation row
- **THEN** the system SHALL update the existing evaluation row for that session and set the session evaluation status to `COMPLETED`

### Requirement: Failed re-evaluation preserves the previous report
The system SHALL preserve the previous successful report data when a re-evaluation attempt fails.

#### Scenario: Re-evaluation fails after a previous report exists
- **WHEN** an interview re-evaluation task fails for a record that already has report data
- **THEN** the system SHALL set the evaluation status to `FAILED`, store the truncated error message, and leave the previous report fields available

### Requirement: Users can trigger re-evaluation from interview history
The frontend SHALL expose a re-evaluation action for eligible interview records in the unified interview history page.

#### Scenario: Trigger re-evaluation from history
- **WHEN** a user clicks the re-evaluation action for an eligible text or voice interview record
- **THEN** the frontend SHALL call the corresponding re-evaluation endpoint, refresh the list, and rely on existing polling while the record is `PENDING` or `PROCESSING`

#### Scenario: Hide re-evaluation during active evaluation
- **WHEN** an interview record evaluation status is `PENDING` or `PROCESSING`
- **THEN** the frontend SHALL not allow another re-evaluation action for that record
