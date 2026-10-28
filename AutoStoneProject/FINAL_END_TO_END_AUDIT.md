# AutoStone End-to-End Audit and Fix Report

## Scope
Audited the supplied AutoStone backend/frontend/database/wrapper project. The existing architecture was retained.

## Graph View fix
The graph now has an explicit root scope API:

`GET /api/jobs/flow?mode=BOX&depth=1`

It returns only definitions where `box_code`/`Job.box` is null **and** `job_type=BOX`.

For an opened BOX:

`GET /api/jobs/{boxId}/flow?mode=BOX&depth=1`

returns only `JobRepository.findByBoxId(boxId)` — immediate children only. No recursive expansion is performed.

Frontend behavior:
- initial Graph View loads root BOXes only;
- single click selects a node;
- double-clicking a BOX opens that BOX;
- each opened BOX requests only its direct children;
- breadcrumbs/back/parent navigation changes the current scope without recursively loading descendants.

## Execution persistence root cause
The previous code did call `save`, but the persistence boundary was unsafe:

1. `EventService.start()` held a transaction across orchestration and remote dispatch.
2. A child execution could be created and then a remote wrapper could be invoked before the enclosing transaction committed.
3. A very fast wrapper/local process could callback while the execution's `RUNNING` state was still uncommitted, causing lookup/race failures.
4. Any later exception in the same start transaction could roll back execution rows that had appeared to be saved.
5. Callback completion set `exec.exitCode`, but `ExecutionStateService.transition()` reloaded the row with `findByIdForUpdate()` and did not copy that exit code, so the callback's exit code could be lost.

## Persistence fix
- Execution creation now uses `REQUIRES_NEW` and `save`/`saveAndFlush` so the execution row is committed before transport starts.
- BOX child execution creation is centralized in `ExecutionCoordinatorService.createBoxChildExecution()` and committed independently.
- Dispatch no longer keeps the database transaction open around the remote wrapper call.
- `RUNNING` is persisted through the authoritative state service before the wrapper is invoked.
- PID/host/log metadata is persisted in a separate `REQUIRES_NEW` transaction after the wrapper returns.
- Callback completion uses `transitionWithExitCode()` so `exitCode`, terminal state, and `endTime` are persisted together.
- Callback updates the existing execution; it never creates another execution.

## Execution model
One workflow/BOX run uses one UUID `runId` across the execution tree. `boxRunId` links each child execution to its containing BOX execution. BOXes are orchestration-only; only executable CMD/JOB definitions are sent to `RemoteExecutionService`/wrapper.

## Dependency model
The dependency resolver remains the automatic trigger mechanism. A dependency-free child becomes eligible when its containing BOX activates. `condition: s(JOB)` is evaluated after the referenced execution reaches SUCCESS. Multiple conditions are evaluated as configured, including `AND`/`OR`/`NOT` forms supported by the existing evaluator.

## Quartz
The existing Quartz scheduler remains the schedule source of truth derived from imported Job definitions. The JIL importer supports built-in `DAILY` and `MONTH_END` calendar conversion. A scheduled root BOX fires `AutostoneQuartzJob`, which calls the same `EventService.start()` path as a manual Start action.

Example:

```jil
insert_job: AST_MAIN_BOX    job_type: box
date_conditions: y
start_times: "10:30"
run_calendar: DAILY
```

This schedules only the root BOX. Child CMDs are dependency/orchestration driven, not separately scheduled by Quartz.

## Validation performed
- Wrapper shell syntax check passed.
- Wrapper contract test passed.
- Input ZIP integrity passed.
- Static verification confirms no direct `JobExecution.setStatus(...)` outside `ExecutionStateService`.
- Frontend API syntax was checked for the modified JavaScript API file.
- Full Maven compilation and full Vite build were not executable in the sandbox because Maven/npm dependencies were not locally installed and external registry access was unavailable.
- A real MySQL/SSH end-to-end run was not claimed because no project database/remote server credentials were available in the sandbox.
