# AutoStone Enterprise Wrapper v6

Controls:
- start <runId> <jobId> <executionId> <callbackUrl> <timeoutSeconds> <workingDir> <command> [secretFile]
- status <pid>
- pause <pid> = SIGSTOP
- resume <pid> = SIGCONT
- stop <pid> = SIGTERM
- kill <pid> = SIGKILL
- signal <pid> TERM|KILL|STOP|CONT

The command process has its own session/process group. The callback supervisor is separate so Stop/Kill cannot terminate the callback supervisor.
A durable start handshake prevents false start failures for very short-lived commands.
