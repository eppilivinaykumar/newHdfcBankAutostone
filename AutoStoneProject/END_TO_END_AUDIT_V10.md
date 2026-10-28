# AutoStone v10 End-to-End Audit / Fixes

This package is based on the uploaded `AutoStone-E2E-Complete-v9-State-Action-Matrix(1).zip` source tree.

## Corrected execution paths

- BOX start creates one workflow `runId` and immediate child execution rows before dependency evaluation.
- Dependency release remains backend-authoritative.
- `A -> B -> C` skip semantics are dependency-aware:
  - A RUNNING + B SKIPPED => C remains WAITING.
  - A SUCCESS + B SKIPPED => C becomes eligible.
  - A FAILED + B SKIPPED => a success-only C condition remains blocked.
- Downstream propagation walks through SKIPPED nodes after a later upstream terminal event, so a skipped B does not need a second state transition to release C.
- Retry remains `RETRY_WAIT -> QUEUED -> RUNNING` and only the final terminal outcome releases downstream work.
- HOLD / auto_hold / ICE / no_execution remain scheduler controls and do not dispatch a wrapper process until eligible.
- BOX is orchestration-only; executable jobs are the only work sent to the wrapper.
- BOX STOP/KILL/CANCEL no longer attempt OS signals against nested BOX execution rows that have no PID.
- BOX PAUSE/RESUME treats nested BOX rows as orchestration state and sends OS signals only to executable jobs.
- BOX roll-up preserves STOPPED/KILLED/CANCELLED when no live child remains, while RUNNING/PAUSED/WAITING still take precedence when sibling work is active.
- Monitoring opened inside a BOX is scoped to that BOX's current workflow run instead of blindly using another run's globally latest execution.
- Flow rendering is null-safe while an expanded BOX's child API request is still loading, addressing the React Flow undefined-data race.

## Verification performed in this environment

PASS:
- Wrapper contract/static checks.
- Wrapper full lifecycle: SUCCESS, FAILED, PAUSED, RESUMED, STOPPED, KILLED, TIMEOUT.
- ConditionEvaluator Java compile/smoke test.
- Source-level end-to-end contract checks for dependency propagation, BOX operator control, run-scoped flow, and React Flow null safety.

Not executed here:
- Full Spring/Maven compilation/tests because Maven is not installed in the environment and the Maven Wrapper could not download Maven Central dependencies.
- Full Vite production build because the ZIP's packaged `node_modules` is incomplete/platform-specific; run `npm ci` on the target machine before `npm run build`.
- Live MySQL/SSH/wrapper callback integration against the user's infrastructure.

The package does not claim live production certification; those final checks require the target database, backend configuration, wrapper host, and credentials.
