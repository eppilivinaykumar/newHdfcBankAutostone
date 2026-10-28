# Backend dependency trigger fix

## Required behavior

For JIL such as:

```text
insert_job: TEST_CBSGB.M.ATS_ACTIVE_C job_type: cmd

insert_job: TEST_CBSGB.M.ATS_START_C job_type: cmd
condition: s(TEST_CBSGB.M.ATS_ACTIVE_C)
```

`ATS_ACTIVE_C` is started first. When its execution reaches `SUCCESS`, the dependency listener evaluates `s(TEST_CBSGB.M.ATS_ACTIVE_C)` and automatically queues/starts `ATS_START_C`.

A false starting condition is no longer converted automatically to `SKIPPED`; the dependent execution remains `WAITING` until its condition becomes true or an operator explicitly skips it. A command that actually starts and exits non-zero is transitioned to `FAILED` by the completion callback.

## run_id vs workflow_run_id

- `run_id`: unique UUID for one execution/attempt and is the callback identifier.
- `workflow_run_id`: shared UUID for the BOX/workflow tree so BOX roll-up and dependency context still work.
- BOX children receive a new `run_id` when created.
- Retry attempts receive a new `run_id` too.

This prevents the old problem where job 2 and job 3 were assigned the same `run_id` merely because they belonged to the same BOX.

## Database

New installs include `workflow_run_id`. Existing databases should run `database/fix_duplicate_run_ids.sql` once to backfill it and split old duplicate `run_id` values.

## Frontend

No frontend source files were changed in this fix.
