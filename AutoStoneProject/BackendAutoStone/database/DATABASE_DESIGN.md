# AutoStone Monitoring / Flow Database Design

## Core relationship model

```text
t_as_application ───────────────┐
                                 │
t_as_user ── owner ── t_as_view ──< t_as_view_job >── t_as_job_definition
                                      │
                                      └──< t_as_view_filter

t_as_machine ──< t_as_job_definition ── self(box_code) ──< nested BOX jobs
       │                    │
       └──< machine_health   └──< t_as_job_condition >── parent job_definition

 t_as_job_definition ──< t_as_job_run ──< t_as_job_run_event
          │                    │
          │                    └──< t_as_alert >── t_as_alert_group
          │
          └──< t_as_event_queue ──< t_as_event_queue_error
```

## Monitoring semantics

- A `BOX` is a persisted job-definition row with `job_type=BOX` and `box_code=NULL` at the root.
- A nested BOX uses `box_code` to point to its parent BOX. The UI recursively renders this tree.
- `t_as_job_condition` stores the directed dependency graph. `job_code` is the child and `depends_on_code` is the parent.
- `t_as_job_run` is the execution instance. `run_id` groups every job/BOX execution belonging to one workflow run.
- `box_run_id` rolls child executions up to the containing BOX execution.
- `t_as_job_run_event` is an append-only operational history for status transitions.
- `t_as_event_queue` is the durable operator command/audit queue.
- `t_as_alert` and `t_as_alert_policy` provide persistent monitoring/alert state.
- `t_as_view`, `t_as_view_job`, and `t_as_view_filter` persist saved Monitoring Views independently of the job definition graph.

## Integrity rules

The strong migration adds foreign keys with safe delete behavior, operational indexes, unique execution UUIDs, and view/filter indexes. Cyclic workflow dependencies remain prohibited at the service layer because a SQL foreign key cannot express a graph cycle constraint.

## Flow API contract

`GET /api/jobs/{id}/flow?mode=ALL&depth=20` returns:

- `nodes`: job/BOX definitions with status, host, flags, parent BOX and calculated graph level.
- `edges`: dependency parent -> child links with condition type, exit code and raw expression.
- `mode` and `depth`: explicit traversal controls for future upstream/downstream/BOX views.

The React canvas normalizes all IDs to strings and ignores only edges whose endpoints are genuinely absent. Therefore stale/mixed numeric-vs-string IDs cannot crash rendering.
