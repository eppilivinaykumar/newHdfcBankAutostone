# AutoStone End-to-End Package v7

This package contains the existing AutoStone project plus the standalone execution wrapper.

## Execution contract

Example:
MAIN_BOX -> A, B, C, CHILD_BOX
A -> D, E, F
CHILD_BOX -> H, R

Starting MAIN_BOX creates one workflow run and materializes the workflow executions. Immediate runnable jobs are dispatched according to dependencies/holds. Nested BOX objects are orchestrated by the backend and are never sent to the wrapper as commands.

A runtime activation is represented by an execution row in WAITING/QUEUED/RUNNING as appropriate; this package does not invent a separate ACTIVE database status.

## Controls

RUNNING: Pause, Stop, Kill
PAUSED: Resume, Stop, Kill
WAITING/QUEUED: Hold, Skip where permitted
HOLD: Unhold

Pause/Resume use the same PID (SIGSTOP/SIGCONT). Stop uses SIGTERM. Kill uses SIGKILL. Normal Pause/Stop/Kill do not manufacture HALTED executions.

## Dependency examples

A -> B -> C

If B is held while A succeeds, B remains held and C remains waiting. Unhold B re-evaluates the dependency and queues B.

If B is explicitly skipped after A succeeds, B becomes SKIPPED and the dependency resolver releases C according to the configured success-style dependency semantics.

## Wrapper

StandaloneWrapper/wrapper.sh supports start/status/signal/test. The wrapper starts the command in its own process group, retains the child PID, and sends callback status to the configured backend callback URL.

## Verification performed in this environment

- Wrapper static/contract checks: PASS
- Wrapper runtime outcome checks: PASS (SUCCESS, FAILED, PAUSED, RESUMED, STOPPED, KILLED, TIMEOUT)
- Backend/frontend source-contract checks: PASS
- Cron source-contract checks: PASS

A live Spring + MySQL + SSH/remote-host + browser integration run still needs to be performed in the target deployment environment. This package does not claim that infrastructure-level test was executed here.
