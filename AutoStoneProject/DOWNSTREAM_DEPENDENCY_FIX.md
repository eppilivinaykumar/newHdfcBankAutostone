# Downstream dependency fix

This update fixes the case where a parent JOB is started directly and then succeeds, but its dependent JOB has no `t_as_job_run` row yet.

## What changed

- `DependencyResolverService.onExecutionStateChanged()` now runs in a new transaction after the parent state is committed. This also keeps lazy JPA dependency relationships usable during downstream processing.
- `releaseDownstream()` now creates the dependent `JobExecution` in `WAITING` when no execution exists for the current `runId`.
- The existing `condition_expression`, such as `s(TEST_CBSGB.M.ATS_ACTIVE_C)`, is then evaluated against the parent execution in the same run.
- When the condition is true, the dependent command is queued and dispatched automatically.
- The sample `TEST_CBSGB_MONTHLY_EOD_B_FULL.jil` files no longer contain `auto_hold: y` on `ATS_START_C` or the PREEOD BOX, so the example flow is fully automatic.

Expected flow:

`ATS_ACTIVE_C RUNNING -> SUCCESS`  => create `ATS_START_C` row in `t_as_job_run` => evaluate `s(ATS_ACTIVE_C)` => `ATS_START_C QUEUED/RUNNING` => execute `EODStart.sh`.
