# AutoStone E2E Release Audit v8

## Verified in this package
- Quartz CRON dependency present.
- Enabled CRON jobs are rebuilt from the DB at application startup.
- CRON trigger uses the configured timezone.
- Schedule start/end are converted using the configured timezone.
- FIRE_NOW and DO_NOTHING misfire policies are wired.
- CRON trigger invokes the same `EventService.start(jobId, null)` path as manual Start.
- Create/update schedules after commit; update/delete unschedule old Quartz triggers first.
- Schedule enable registers the CRON trigger; schedule disable removes only the future trigger and does not alter an in-flight JobExecution.
- Frontend exposes schedule enable/disable controls for CRON jobs.
- Wrapper contract/runtime tests pass for success, failure, pause/resume, stop, kill and timeout.
- Production source contract passes, including frontend `/event` endpoint coverage and execution invariants.
- DB/SSH/callback settings are environment-configurable; no developer-specific SSH key path is required by the release package.

## Important deployment limitation
This audit is source/package-level plus standalone-wrapper runtime verification. A real Spring Boot + MySQL + Quartz + SSH target + browser SSE/WebSocket deployment was not executed in this environment. Before production use, run the deployment smoke test against the target infrastructure.
