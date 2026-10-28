package com.mybank.autostone.service;

import com.mybank.autostone.entity.JobStatus;
import com.mybank.autostone.entity.JobType;
import java.util.UUID;

/** Single domain event emitted by the authoritative execution-state transition service. */
public record ExecutionStateChangedEvent(
        Long executionId,
        Long jobId,
        UUID runId,
        UUID workflowRunId,
        JobStatus oldStatus,
        JobStatus newStatus,
        Long parentBoxId,
        JobType jobType,
        String message) {}
