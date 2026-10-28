# AutoStone – corrected enterprise execution build

This build keeps the existing React UI and wires it to the Spring Boot execution engine and the supplied AutoSys/DolphinScheduler-style database model.

## Important fixes

1. React action URLs now match the Spring `/event/*` controllers. The previous UI called `/api/jobs/{id}/run`, `/stop`, `/pause`, etc. while the actual event controller exposed `/event/start`, `/event/stop`, `/event/pause`, etc.
2. Dependency jobs are WAITING until their condition is satisfied. Manual Start no longer bypasses a starting condition.
3. Dependency-free BOX children are activated automatically.
4. BOX children with external dependencies are evaluated correctly.
5. Conditions can use the current run when a referenced job belongs to the run, and the latest known run when it is an external dependency.
6. BOX Stop/Kill/Cancel affects all non-terminal children in the same run.
7. BOX Resume resumes paused child processes as well as the BOX execution.
8. Operator actions are persisted to `t_as_event_queue` as an audit/command trail.
9. Job definition versions are incremented on update and snapshots are written to `t_as_job_definition_log`.
10. Execution instances are stored in `t_as_job_run`, with a UUID run id retained for the current frontend/callback contract.
11. Failure/timeout/server-unavailable alerts are persisted to `t_as_alert` according to `t_as_alert_policy`.
12. `/api/health` checks application and MySQL connectivity.
13. The MySQL password is read from `DB_PASSWORD`; it is not hard-coded.
14. Remote wrapper callbacks remain protected by `X-AutoStone-Callback-Secret`.

## Database

For a new database run:

    database/autostone_schema.sql

If the supplied core `t_as_*` schema already exists, run:

    database/autostone_migration.sql
    database/autostone_flow_upgrade.sql
    database/autostone_strong_migration.sql

For a new database, run `autostone_schema.sql` first, then the three migrations above to enable the full monitoring/view model and relational integrity.

The core tables are:

- t_as_application
- t_as_user
- t_as_machine
- t_as_calendar
- t_as_job_definition
- t_as_job_definition_log
- t_as_job_condition
- t_as_event_queue
- t_as_event_queue_error
- t_as_job_run
- t_as_global_variable
- t_as_alert
- t_as_alert_group

`t_as_alert_policy` and the operational columns in the job/run tables are AutoStone extensions needed by the existing UI and execution controls.

## Backend configuration

Set these environment variables before starting Spring Boot:

    DB_URL=jdbc:mysql://localhost:3306/autostone?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    DB_USERNAME=root
    DB_PASSWORD=<your-password>
    SERVER_PORT=8081

For remote execution:

    AUTOSTONE_SSH_USER=<ssh-user>
    AUTOSTONE_SSH_KEY_PATH=<private-key-path-on-backend>
    AUTOSTONE_SSH_PORT=22
    AUTOSTONE_WRAPPER_PATH=/opt/autostone/wrapper.sh
    AUTOSTONE_CALLBACK_BASE_URL=http://<backend-host>:8081
    AUTOSTONE_CALLBACK_SECRET=<shared-secret>
    AUTOSTONE_CALLBACK_SECRET_FILE=/etc/autostone/callback-secret

The callback URL must be reachable from the remote Linux execution server. `localhost` is only correct when the wrapper runs on the same host as the Spring Boot backend.

Deploy `scripts/wrapper.sh` to the configured remote wrapper path and make it executable. Put the same callback secret in the configured secret file on every execution server.

## Frontend

Create `.env` from `.env.example` when the frontend must call a non-default backend:

    VITE_API_BASE_URL=

During Vite development the proxy target is controlled by:

    VITE_DEV_BACKEND_URL=http://localhost:8081

Run:

    npm install
    npm run dev

## Execution flow

React -> Vite proxy -> Spring REST API -> MySQL -> Event/Dependency engine -> Quartz or manual trigger -> SSH/local execution -> wrapper -> remote process -> callback -> t_as_job_run -> dependency release -> monitoring refresh.

## Video-reference scope

The supplied AutoSys videos show Dashboard, Monitoring, Quick View, View Editor, Jobs/Alerts and Flow interactions. The uploaded project already contains the Dashboard/Monitoring/Quick View/Jobs/Flow pages, so this correction preserves those existing screens. The current project does not contain a Reporting page or a View Editor page, so those two video screens cannot be made clickable without adding UI routes/components; this build does not redesign the UI because that was explicitly requested.
