package com.mybank.autostone.service;

import com.mybank.autostone.entity.ConcurrencyPolicy;
import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobStatus;
import com.mybank.autostone.remote.RemoteCommandResult;
import com.mybank.autostone.remote.RemoteExecutionService;
import com.mybank.autostone.repository.JobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns dispatch decisions between the API/scheduler/dependency engine and the actual
 * execution transport. This keeps SSH/local execution out of controllers and gives
 * QUEUE concurrency policy real semantics instead of returning the currently running run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionCoordinatorService {

    private static final List<JobStatus> ACTIVE = List.of(
            JobStatus.QUEUED, JobStatus.WAITING, JobStatus.RUNNING,
            JobStatus.PAUSED, JobStatus.RETRY_WAIT
    );

    private final JobExecutionRepository executionRepository;
    private final RemoteExecutionService remoteExecutionService;
    private final ExecutionStateService stateService;

    public Optional<JobExecution> activeExecution(Long jobId) {
        return executionRepository.findFirstByJobIdAndStatusInOrderByIdDesc(jobId, ACTIVE);
    }

    /** Creates the persisted execution in QUEUED state. */
    @Transactional(propagation = REQUIRES_NEW)
    public JobExecution createQueuedExecution(Job job, UUID runId, Integer attempt) {
        int runNumber = executionRepository.findLatestForJob(job.getId()).size() + 1;
        JobExecution execution = JobExecution.builder()
                .job(job)
                .jobVersion(job.getVersion() == null ? 1 : job.getVersion())
                .runNumber(runNumber)
                .machineId(job.getMachineId())
                .runId(runId)
                .workflowRunId(runId)
                .status(JobStatus.CREATED)
                .attempt(attempt == null ? 1 : attempt)
                .message("Created for execution")
                .build();
        execution = executionRepository.save(execution);
        return stateService.transition(execution, JobStatus.QUEUED, "Queued for execution");
    }

    /** Persists a BOX child execution in its own committed transaction before any child can dispatch. */
    @Transactional(propagation = REQUIRES_NEW)
    public JobExecution createBoxChildExecution(Job child, Long boxRunId, UUID workflowRunId) {
        int runNumber = executionRepository.findLatestForJob(child.getId()).size() + 1;
        UUID childRunId = workflowRunId;
        JobExecution execution = JobExecution.builder()
                .job(child).jobVersion(child.getVersion() == null ? 1 : child.getVersion())
                .runNumber(runNumber).machineId(child.getMachineId()).boxRunId(boxRunId).runId(childRunId).workflowRunId(workflowRunId)
                .status(JobStatus.CREATED).attempt(1).message("Created in BOX execution").build();
        execution = executionRepository.saveAndFlush(execution);
        JobExecution persisted = stateService.transition(execution, JobStatus.WAITING,
                Boolean.TRUE.equals(child.getAutoHold()) ? "On Hold - AutoSys auto_hold is enabled" : "Waiting for dependencies");
        if (Boolean.TRUE.equals(child.getAutoHold())) {
            persisted.setRuntimeHold(true);
            persisted.setHoldReason("AUTO_HOLD");
            persisted = executionRepository.saveAndFlush(persisted);
        }
        return persisted;
    }

    /**
     * Dispatches a queued execution only if no other active execution for the same job
     * is present. Safe to call repeatedly after callbacks or scheduler ticks.
     */
    public boolean dispatchIfEligible(JobExecution execution) {
        if (execution == null || execution.getStatus() != JobStatus.QUEUED) return false;

        Job job = execution.getJob();
        if (Boolean.TRUE.equals(execution.getRuntimeHold()) || Boolean.TRUE.equals(job.getHold()) || Boolean.TRUE.equals(job.getIce()) || Boolean.TRUE.equals(job.getNoExecution())) {
            stateService.transition(execution, JobStatus.WAITING, Boolean.TRUE.equals(execution.getRuntimeHold()) || Boolean.TRUE.equals(job.getHold()) ? "Execution held" :
                    Boolean.TRUE.equals(job.getIce()) ? "Execution is on ice" : "Execution suppressed by no-execution mode");
            return false;
        }
        if (job.getConcurrencyPolicy() == null || job.getConcurrencyPolicy() == ConcurrencyPolicy.ALLOW) {
            return dispatch(execution);
        }
        Optional<JobExecution> other = executionRepository
                .findFirstByJobIdAndStatusInOrderByIdDesc(job.getId(), ACTIVE.stream()
                        .filter(s -> s != JobStatus.QUEUED)
                        .toList());
        if (other.isPresent() && !other.get().getId().equals(execution.getId())) return false;

        return dispatch(execution);
    }

    public boolean dispatch(JobExecution execution) {
        if (execution == null || execution.getStatus() != JobStatus.QUEUED) return false;
        Job job = execution.getJob();
        stateService.transition(execution, JobStatus.RUNNING, "Execution dispatched");

        RemoteCommandResult result = remoteExecutionService.dispatchStart(job, execution);
        if (!result.isSuccess()) {
            stateService.transition(execution, JobStatus.FAILED, result.getErrorMessage());
            log.error("Dispatch failed for job {} execution {}: {}", job.getId(), execution.getId(), result.getErrorMessage());
            return false;
        }

        persistDispatchMetadata(execution.getId(), parsePid(result.getPid()), job.getHostName(), result.getLogPath());
        return true;
    }

    /**
     * Commits transport metadata independently from the dispatch transaction so a fast
     * wrapper callback can never race an uncommitted execution row. A callback may already
     * have made the execution terminal; this update intentionally changes only PID/host/log.
     */
    @Transactional(propagation = REQUIRES_NEW)
    public void persistDispatchMetadata(Long executionId, Integer pid, String host, String logPath) {
        JobExecution current = executionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution disappeared before transport metadata could be stored: " + executionId));
        current.setPid(pid);
        current.setHost(host);
        current.setLogPath(logPath);
        executionRepository.saveAndFlush(current);
    }

    /**
     * Called after a terminal execution. Starts the oldest queued execution for this job,
     * preserving FIFO ordering and the configured QUEUE policy.
     */
    public void dispatchNextQueued(Long jobId) {
        Optional<JobExecution> next = executionRepository
                .findFirstByJobIdAndStatusOrderByIdAsc(jobId, JobStatus.QUEUED);
        next.ifPresent(this::dispatchIfEligible);
    }

    private Integer parsePid(String pid) {
        if (pid == null || pid.isBlank()) return null;
        try { return Integer.valueOf(pid); } catch (NumberFormatException e) { return null; }
    }

    public boolean allowsParallel(Job job) {
        return job.getConcurrencyPolicy() == null || job.getConcurrencyPolicy() == ConcurrencyPolicy.ALLOW;
    }
}
