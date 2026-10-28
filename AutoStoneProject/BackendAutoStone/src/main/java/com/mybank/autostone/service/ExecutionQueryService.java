package com.mybank.autostone.service;

import com.mybank.autostone.dto.*;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.remote.RemoteExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class ExecutionQueryService {
    private final JobExecutionRepository repository;
    private final RemoteExecutionService remoteExecutionService;

    public List<ExecutionResponse> byJob(Long jobId) { return repository.findByJobIdOrderByIdDesc(jobId).stream().map(this::map).toList(); }
    public ExecutionResponse get(Long id) { return map(repository.findById(id).orElseThrow(() -> new NoSuchElementException("Execution not found: " + id))); }
    public ExecutionLogResponse logs(Long id, int maxChars) {
        JobExecution e = repository.findById(id).orElseThrow(() -> new NoSuchElementException("Execution not found: " + id));
        return ExecutionLogResponse.builder().executionId(id).stdout(remoteExecutionService.readExecutionLog(e, maxChars)).stderr("").build();
    }
    private ExecutionResponse map(JobExecution e) { return ExecutionResponse.builder().id(e.getId()).jobId(e.getJob().getId()).jobName(e.getJob().getName()).runId(e.getRunId()).status(e.getStatus()).attempt(e.getAttempt()).exitCode(e.getExitCode()).pid(e.getPid() == null ? null : String.valueOf(e.getPid())).host(e.getHost()).message(e.getMessage()).logPath(e.getLogPath()).startTime(e.getStartTime()).endTime(e.getEndTime()).build(); }
}
