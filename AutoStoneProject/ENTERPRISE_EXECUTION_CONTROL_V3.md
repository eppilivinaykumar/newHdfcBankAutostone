# AutoStone Enterprise Execution Control v3

This package keeps the existing AutoStone architecture and aligns frontend, backend, and wrapper behavior.

## Operator control contract

- RUNNING: Pause, Stop, Kill.
- PAUSED: Resume, Stop, Kill.
- WAITING / QUEUED / CREATED / HALTED: Hold (when not already held) and Skip where applicable.
- HOLD/UNHOLD are scheduler controls only and never signal a live OS process.
- PAUSE/RESUME/STOP/KILL operate on the same execution PID/process group.
- HALTED is a workflow/dependency state only. It is not sent to the wrapper.
- SKIPPED is terminal and is treated as satisfied for downstream `s(JOB)` dependency release per requested AutoStone behavior.

## Wrapper contract

`wrapper.sh start <run_id> <job_id> <execution_id> <callback_url> <timeout> <working_dir> <command> [secret_file]`
`wrapper.sh status <pid>`
`wrapper.sh pause <pid>`
`wrapper.sh resume <pid>`
`wrapper.sh stop <pid>`
`wrapper.sh kill <pid>`
`wrapper.sh signal <pid> <TERM|KILL|STOP|CONT>`

Signals:
- Pause = SIGSTOP
- Resume = SIGCONT
- Stop = SIGTERM
- Kill = SIGKILL

The wrapper never receives HOLD, UNHOLD, SKIP, or HALTED.

## Run identity

All executions created inside one BOX/dependency workflow share the same `runId`/`workflowRunId`.
`executionId` is the unique callback identity.
`boxRunId` identifies the immediate parent BOX execution.

## Key fixes

1. Removed HOLD-as-PAUSE behavior for running jobs.
2. Reject HOLD when the selected execution is RUNNING or PAUSED.
3. Unhold clears runtime hold and re-evaluates dependencies without SIGCONT.
4. Auto-hold remains WAITING + runtimeHold instead of PAUSED.
5. SKIPPED releases downstream dependencies; `s(SKIPPED_JOB)` is treated as satisfied as requested.
6. HALTED is not sent to the wrapper and is shown distinctly in Monitoring UI.
7. Child BOX/dependency/retry executions share workflow runId.
8. Wrapper state/log files include executionId to avoid collisions.
9. Wrapper supports status plus direct pause/resume/stop/kill aliases.
10. Frontend action availability follows backend state semantics.

## Validation performed

- `bash -n BackendAutoStone/scripts/wrapper.sh`: passed.
- Wrapper START -> PAUSE -> RESUME -> KILL using same PID: passed.
- Frontend source/API action mapping was statically checked.
- Full frontend build could not complete in this sandbox because the uploaded `node_modules` contains a missing platform-native Rolldown optional binding.
- Full Maven tests could not execute because Maven is not installed locally and Maven Wrapper download access to Maven Central was unavailable.
