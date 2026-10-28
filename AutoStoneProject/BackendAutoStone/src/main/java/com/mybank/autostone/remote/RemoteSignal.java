package com.mybank.autostone.remote;

/**
 * Maps AutoStone actions to the actual POSIX signal the wrapper sends to the tracked PID.
 * HALT is intentionally absent here - it's a pure workflow-graph concept (block downstream
 * jobs), never a process signal, so it never reaches RemoteExecutionService.
 */
public enum RemoteSignal {
    STOP("TERM"),
    KILL("KILL"),
    PAUSE("STOP"),
    RESUME("CONT");

    private final String posixSignal;

    RemoteSignal(String posixSignal) {
        this.posixSignal = posixSignal;
    }

    public String posixSignal() {
        return posixSignal;
    }
}