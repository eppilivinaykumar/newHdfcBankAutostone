package com.mybank.autostone.entity;

public enum JobStatus {
    CREATED,
    QUEUED,
    WAITING,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRY_WAIT,
    STOPPED,
    KILLED,
    PAUSED,
    TIMEOUT,
    SKIPPED,
    CANCELLED,
    HALTED
}
