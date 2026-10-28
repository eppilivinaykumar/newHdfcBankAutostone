# AutoStone BOX / Nested BOX / JOB runtime database design

## Runtime tables used by the current implementation

### `t_as_job_definition`

One row per JOB or BOX definition.

- `code` = definition primary key
- `job_name` = globally unique job/BOX name
- `job_type` = `JOB` or `BOX`
- `box_code` = direct parent BOX definition ID; `NULL` means top-level
- `condition_expression` = AutoSys-style condition such as `s(JOB_A)`
- `auto_hold` = AutoSys `auto_hold` definition flag
- `hold`, `ice`, `no_execution` = runtime/operator controls
- schedule fields (`trigger_type`, `cron_expression`, `start_times`, `run_calendar_name`, etc.) remain on the definition

This is an adjacency-list hierarchy. A nested BOX is simply another `t_as_job_definition` row whose `box_code` points to its parent BOX.

### `t_as_job_condition`

Materialized dependency relationships extracted from conditions.

- `job_code` = dependent job/BOX
- `depends_on_code` = prerequisite job/BOX
- `condition_type` / `raw_expression` = dependency semantics

The condition expression remains on the job definition while the relationship table supports efficient dependency traversal.

### `t_as_job_run`

One row per execution instance of a JOB or BOX.

- `id` = execution ID and immutable history key
- `job_code` = definition being executed
- `run_id` = workflow/run correlation ID; **all executions in one BOX workflow share this UUID**
- `box_run_id` = parent BOX execution ID, forming the execution tree
- `status` = authoritative execution state
- `attempt` / `run_number` = retry/history identity
- PID, host, exit code, timestamps and log paths retain execution details

`run_id` must not be globally unique. A main BOX, nested BOXes and their JOB executions intentionally share the same `run_id` so the workflow can be correlated.

### `t_as_job_run_event`

Durable state-transition event history.

Each accepted state change records:

`execution_id -> old_status -> new_status -> message -> timestamp`

The SSE monitor reads these events and includes the changed JOB plus all containing BOXes.

## Example execution tree

Definition tree:

```text
MAIN_BOX (box_code = NULL)
├── JOB_1 (box_code = MAIN_BOX)
└── CHILD_BOX (box_code = MAIN_BOX)
    ├── JOB_2 (box_code = CHILD_BOX)
    └── NESTED_BOX (box_code = CHILD_BOX)
        ├── JOB_3 (box_code = NESTED_BOX)
        └── JOB_4 (box_code = NESTED_BOX)
```

Runtime tree for one execution:

```text
MAIN_BOX execution #100, run_id = R
├── JOB_1 execution #101, run_id = R, box_run_id = 100
└── CHILD_BOX execution #102, run_id = R, box_run_id = 100
    ├── JOB_2 execution #103, run_id = R, box_run_id = 102
    └── NESTED_BOX execution #104, run_id = R, box_run_id = 102
        ├── JOB_3 execution #105, run_id = R, box_run_id = 104
        └── JOB_4 execution #106, run_id = R, box_run_id = 104
```

## BOX state precedence

The backend `ExecutionStateService` is the only authority for derived BOX state.

1. Explicit BOX `KILLED`, `STOPPED` or `CANCELLED` is preserved.
2. BOX hold produces `PAUSED` while the execution is non-terminal.
3. Required child `FAILED` / `TIMEOUT` / `KILLED` / `STOPPED` / `CANCELLED` produces `FAILED`.
4. Any required child `RUNNING` produces `RUNNING`.
5. Any child `PAUSED` produces `PAUSED`.
6. Missing child execution, `WAITING`, `QUEUED`, `RETRY_WAIT` or `HALTED` produces `WAITING`.
7. All children `SUCCESS` or `SKIPPED` produces `SUCCESS`.

This precedence is applied recursively from the changed child through every containing BOX.

## Graph scope

`GET /api/jobs/{boxId}/flow?mode=BOX&depth=1` returns only the direct children of that BOX. Opening a nested BOX calls the same endpoint with the nested BOX ID. Descendants are not flattened into the parent's graph.
