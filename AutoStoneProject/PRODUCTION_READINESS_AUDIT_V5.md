# AutoStone Production/Demo Readiness Audit v5

## Executed checks

- Wrapper Bash syntax: PASS
- Wrapper static contract: PASS
- Wrapper runtime START -> PAUSE -> RESUME -> STOP: PASS
- Wrapper runtime START -> PAUSE -> KILL: PASS
- Wrapper TIMEOUT callback status: PASS
- Frontend ESLint: PASS with 2 React Hook warnings, 0 errors
- Frontend `/event` API to backend controller mapping: PASS (21 endpoints)
- Source production contract (SKIPPED/HALTED/HOLD/shared runId/wrapper callback): PASS
- ZIP/source integrity: PASS

## Critical behavior verified in source

- RUNNING supports Pause, Stop and Kill.
- PAUSED supports Resume, Stop and Kill using the same recorded process PID.
- HOLD is rejected for RUNNING/PAUSED executions and does not call the wrapper.
- UNHOLD clears scheduler hold and re-evaluates dependencies.
- Operator SKIP calls downstream dependency release immediately.
- SKIPPED maps to a resolved SUCCESS outcome for success-style dependency evaluation, so A -> B -> C can continue when B is explicitly skipped.
- RUNNING/PAUSED do not transition directly to HALTED.
- HALTED is not a wrapper action or POSIX signal.
- Nested BOX child executions share the workflow runId and keep boxRunId as the runtime parent execution.
- Wrapper callback is keyed by runId + jobId + executionId and protected by X-AutoStone-Callback-Secret.

## Production changes in v5

- Fixed Linux CRLF failure in `scripts/test-wrapper-contract.sh`.
- Hardened wrapper timeout command invocation to avoid nested command quoting problems.
- Fixed held BOX derived state to WAITING instead of PAUSED.
- Added `application-prod.properties`; production DB credentials, callback secret, callback URL, SSH user/key, and allowed origins must be supplied externally.
- Fixed frontend lint errors in React Flow node exports and Vite environment handling.
- Added `test-production-source-contract.py`.

## Environment-limited checks

The complete Spring Boot Maven test suite could not execute in this audit environment because Maven is not installed and Maven Wrapper cannot download the Maven distribution from Maven Central here.

The Vite production bundle could not be generated from the uploaded `node_modules` because the ZIP contains platform-specific dependencies that are missing the Linux Rolldown native binding. The production package therefore excludes `node_modules`; run `npm ci` on the deployment target before `npm run build`.

These two items must be run in the target/demo environment before promotion:

```bash
cd BackendAutoStone
./mvnw test

cd ../AutoStoneFrontend_final
npm ci
npm run lint
npm run build
```

## Required production environment values

Activate the profile with:

```bash
SPRING_PROFILES_ACTIVE=prod
```

Set at minimum:

- DB_URL
- DB_USERNAME
- DB_PASSWORD
- AUTOSTONE_SSH_USER
- AUTOSTONE_SSH_KEY_PATH
- AUTOSTONE_CALLBACK_BASE_URL
- AUTOSTONE_CALLBACK_SECRET
- AUTOSTONE_ALLOWED_ORIGINS

Deploy the same callback secret to each execution host in `AUTOSTONE_CALLBACK_SECRET_FILE` (default `/etc/autostone/callback-secret`).

## Final verification pass (2026-09-29)

Additional checks executed against the v5 source tree:

- Production source contract: PASS (21 frontend `/event` endpoints matched to backend controller mappings; SKIPPED/HALTED/shared workflow invariants checked).
- Wrapper shell contract: PASS.
- Wrapper enterprise runtime: PASS.
- Wrapper full outcome suite: PASS for SUCCESS, FAILED, PAUSED, RESUMED, STOPPED, KILLED, TIMEOUT.
- Quartz/CRON source contract: PASS for startup reconstruction, enabled-only filtering, CRON-only registration, timezone-aware trigger/window, both misfire policies, and dispatch through the same `EventService.start()` path.
- Fixed wrapper fast-command start race using a durable start handshake.
- Fixed CRON scheduleStartTime/scheduleEndTime conversion to use the configured job timezone rather than the backend JVM default timezone.

Environment limitations: full Maven/Spring compilation and integration tests could not be executed because Maven distribution download is unavailable in this execution environment. Full browser/Vite build could not be executed because npm dependency installation is network-dependent here. These are deployment-machine checks and are explicitly not claimed as passed in this audit.
