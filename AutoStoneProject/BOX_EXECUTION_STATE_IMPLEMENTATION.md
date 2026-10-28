# AutoStone BOX -> Nested BOX -> JOB Execution/State Implementation

This build keeps the existing AutoStone React + React Flow + Bootstrap frontend and Spring Boot/JPA/Quartz/SSH backend. The implementation is an enhancement of the existing execution model, not a separate workflow engine.

## Authoritative execution state

`ExecutionStateService` is the single backend state-transition and BOX roll-up mechanism.

It:
- validates JOB/BOX execution transitions against the existing `JobStatus` enum;
- records every state transition in `t_as_job_run_event`;
- derives a BOX state from its immediate children in the same `run_id`;
- recursively propagates a child state change through all parent BOXes;
- preserves explicit operator terminal states such as KILLED/STOPPED/CANCELLED;
- exposes a pure BOX-state derivation used by monitoring/dashboard responses.

## BOX execution

Starting a BOX creates a real BOX execution row and a shared `run_id`. Immediate children receive execution rows with the same run id and the BOX execution id in `box_run_id`. Dependency resolution then activates eligible children.

A nested BOX is treated as a container, not as a remote command. Its activation creates its own child execution rows. This repeats recursively for nested BOX levels.

Dependency-free siblings can run in parallel. Dependency-bound jobs remain WAITING until the existing dependency evaluator releases them.

## Completion propagation

JOB completion updates the JOB execution and invokes BOX roll-up. When a nested BOX reaches SUCCESS/FAILED/etc., its parent is recalculated, then the next parent, until the top-level BOX is reached.

## Graph scope

`GET /api/jobs/{id}/flow?mode=BOX&depth=1` returns only the immediate children of the selected BOX. The frontend opens a BOX by calling the same endpoint for that BOX id. Therefore descendants are not leaked into a parent BOX graph.

The frontend maintains a breadcrumb path and Back navigation for:

`Monitoring -> MAIN_BOX -> CHILD_BOX -> NESTED_BOX`

## Real-time updates

SSE now consumes durable `t_as_job_run_event` state-change events. A changed child causes the stream to include the child and all containing BOXes. The frontend refreshes its current graph scope and dashboard summary without a full page reload.

## Database

The existing `t_as_job_definition.box_code` relation remains the parent/child hierarchy. `t_as_job_run.box_run_id` represents the containing BOX execution for a run. `t_as_job_run_event` stores state transitions for reliable monitoring deltas.

## Important validation limitation

The source was statically inspected and modified, but this environment did not have Maven dependencies cached and could not complete Maven compilation, and the frontend registry packages were not cached for an offline build. Run the project's normal `mvn clean test` / `npm install` / `npm run build` in the development environment before production deployment.


## JIL conditions used by the supplied screenshots

The importer supports `box_name` as the parent-BOX relationship and parses AutoSys `condition` expressions such as `s(TEST_CBSGB.M.ATS_ACTIVE_C)`. The supplied flow therefore becomes a real hierarchy rather than a flat import. `date_conditions: y`, `start_times: "19:00"`, and `run_calendar: MONTH_END` are converted to the existing Quartz scheduling model. `auto_hold: y` is retained as a definition flag and applied to an in-box child when its containing BOX starts; the child then requires an explicit OFF_HOLD to become eligible.
