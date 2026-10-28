# AutoStone Monitoring State → Color → Action → Backend Matrix

This release applies the monitoring state presentation to the existing frontend/backend without replacing the execution architecture.

| UI state | Backend source | Color | Allowed actions | Backend transition / effect |
|---|---|---|---|---|
| ACTIVE | QUEUED | Purple | Hold, Skip | QUEUED → WAITING for Hold; QUEUED → SKIPPED for Skip |
| ACTIVATED | WAITING | Light blue | Hold, Skip | WAITING → runtime hold; WAITING → SKIPPED |
| STARTING | Reserved display state | Cyan | Stop/Kill only if backend exposes STARTING | Current dispatcher does QUEUED → RUNNING; no new persisted state added |
| RUNNING | RUNNING | Green | Pause, Stop, Kill | Pause → PAUSED/SIGSTOP; Stop → STOPPED/SIGTERM; Kill → KILLED/SIGKILL |
| PAUSED | PAUSED | Yellow | Resume, Stop, Kill | Resume → RUNNING/SIGCONT; Stop → STOPPED/SIGTERM; Kill → KILLED/SIGKILL |
| STOPPED | STOPPED | Dark red | Start/Restart | New execution is created by Start/Restart |
| KILLED | KILLED | Red | Start/Restart | New execution is created by Start/Restart |
| HOLD | WAITING + hold/runtimeHold | Amber | Unhold | Hold clears runtime hold and re-evaluates dependencies |
| SUCCESS | SUCCESS | Blue | Start/Restart | New execution/run |
| FAILED | FAILED | Red | Start/Restart | New execution/run |
| TIMEOUT | TIMEOUT | Magenta | Start/Restart | New execution/run |
| SKIPPED | SKIPPED | Gray | Start/Restart | New execution/run; downstream dependency release remains enabled |
| CANCELLED | CANCELLED | Gray | Start/Restart | New execution/run |
| HALTED | HALTED | Brown | Recovery only | Not sent to wrapper as a process signal |
| ON ICE | ice flag | Cyan-blue | Off Ice | Re-evaluate waiting work |
| NO-EXEC | noExecution flag | Slate | Off No-Execution | Re-evaluate waiting work |
| RESTARTING | RETRY_WAIT | Violet | Scheduler-controlled | RETRY_WAIT → QUEUED → dispatch |
| INACTIVE | CREATED/no active execution | Gray | Start | CREATED → QUEUED/WAITING → execution flow |

## Backend safety enforcement

The API now rejects direct calls that violate the UI action matrix:

- Stop: RUNNING or PAUSED only.
- Kill: RUNNING or PAUSED only.
- Skip: WAITING or QUEUED only.
- Restart: only when no active execution exists.
- Hold: never on RUNNING or PAUSED; use Pause/Resume/Stop/Kill for live processes.

BOX Stop/Kill still operate recursively on descendants as part of the selected BOX operation.

## Important distinction

`ACTIVE` and `ACTIVATED` are presentation states over existing backend states:

- QUEUED = ACTIVE: execution is eligible/active in the dispatcher.
- WAITING = ACTIVATED: execution participates in the current run but is waiting for dependency/condition resolution.

This avoids adding a second source of truth to the execution database.
