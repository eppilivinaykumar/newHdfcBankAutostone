# AutoStone Enterprise Execution Control v4

## Required behavior

### RUNNING
- Pause -> PAUSED; wrapper SIGSTOP.
- Stop -> STOPPED; wrapper SIGTERM.
- Kill -> KILLED; wrapper SIGKILL.
- Hold/Unhold are rejected/not offered for RUNNING.

### PAUSED
- Resume -> RUNNING; wrapper SIGCONT using the same execution PID.
- Stop -> STOPPED; wrapper SIGTERM.
- Kill -> KILLED; wrapper SIGKILL.
- Hold/Unhold are not process controls.

### WAITING / QUEUED / CREATED
- Hold blocks dispatch without invoking the wrapper.
- Unhold clears the runtime hold and re-evaluates dependencies.
- Skip is terminal and immediately releases downstream dependencies according to the condition evaluator.

### HALTED
HALTED is retained only for compatibility/recovery of legacy data. Normal Pause, Stop, Kill, Cancel and Resume paths do not create HALTED executions, and HALTED is never sent to the wrapper.

### A -> B -> C skip case
If A is running, B is explicitly skipped, and B is a dependency of C, then:
1. B becomes SKIPPED.
2. The backend immediately re-evaluates C in the same workflow run.
3. SKIPPED is treated as a successful/resolved dependency outcome by the existing condition evaluator mapping.
4. C is queued/dispatched when otherwise eligible.

### Wrapper callback safety
The wrapper callback supervisor runs in a separate process group from the command. STOP/KILL signal only the command process group, so they cannot kill the callback supervisor. Wrapper callbacks include an explicit `status` field while retaining `exitCode` compatibility.

### Auto-Hold
`auto_hold` creates a WAITING execution with `runtimeHold=true`; it does not use PAUSED because PAUSED means a real OS process is suspended.
