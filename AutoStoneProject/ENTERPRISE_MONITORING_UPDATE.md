# AutoStone Enterprise Monitoring Update

This update aligns the monitoring workspace with the supplied reference video and screenshot while preserving the existing AutoStone execution APIs.

## Monitoring workspace
- Summary, Jobs and Alerts, and Flow tabs are interactive.
- Flow canvas supports zoom in/out, fit, mouse-wheel zoom, drag-to-pan, grid toggle, search focus, undo/redo of viewport changes, and refresh.
- Auto Refresh can be enabled/disabled from the left Views pane.
- Search and status filters apply to the monitoring tree and Jobs/Alerts table.
- Selected job state is preserved across refreshes.
- Start, Pause, Stop, Resume, Rerun and cron schedule enable/disable use the existing backend event APIs.
- Job Details remains linked from the monitoring workspace.

## Backend
- Added `/api/monitoring/alerts` for failure/timeout/halted execution alerts.
- Fixed Quartz cron trigger generic typing by using `CronTrigger`.
- Updated SSH library dependency to maintained JSch (`com.github.mwiede:jsch`).

## Validation
- Frontend ESLint passes in the supplied environment.
- Frontend source was syntax-parsed successfully.
- Full Vite build could not be executed in the sandbox because the supplied `node_modules` lacks the platform-specific Rolldown native binding; run `npm install` on the development machine before `npm run build`.
- Full Maven compile could not be executed in the sandbox because Maven was not installed and the wrapper could not download Maven from the external repository.

## AutoSys-style Monitoring update

The Monitoring tab now supports:

- Summary at selected BOX/view scope, including total/running/success/failure/waiting/paused and hold/ice/no-execution counts.
- Server connectivity summary and latest alerts.
- Jobs and Alerts table with Open navigation.
- Flow graph based on real `dependsOnJobIds`, including branch/fan-out and merge/fan-in dependency edges.
- Zoom in/out, mouse-wheel zoom, drag-to-pan, fit, undo/redo, grid toggle, refresh and Overview/minimap toggle.
- Right-click context menu on jobs and BOXes with: Open, Start, Force Start, Pause/Resume, Stop, Rerun, On Hold/Off Hold, On Ice/Off Ice, On No Execution/Off No Execution, Release Resources, Kill, Send Event and Cancel Event.
- Auto refresh every 5 seconds, with manual refresh.

### Backend action endpoints

All operator actions are implemented under `/event` and keyed by `jobId`:

`/force-start`, `/hold`, `/off-hold`, `/ice`, `/off-ice`, `/no-execution`, `/off-no-execution`, `/release-resources`, `/kill`, `/send-event`, `/cancel-event`, plus the existing start/stop/pause/resume/restart and schedule enable/disable endpoints.

`hold`, `ice`, and `noExecution` are persisted on the Job entity and checked by the dispatch coordinator so suppressed jobs do not accidentally execute. `Send Event` is currently recorded as an operator event/message on the active execution; a future event-condition engine can consume the same event payload without changing the Monitoring UI contract.
