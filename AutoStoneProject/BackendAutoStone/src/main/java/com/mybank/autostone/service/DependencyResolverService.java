package com.mybank.autostone.service;

import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.JobDependency;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobStatus;
import com.mybank.autostone.entity.JobType;
import com.mybank.autostone.repository.JobDependencyRepository;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import com.mybank.autostone.util.ConditionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DependencyResolverService {

    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final ExecutionCoordinatorService executionCoordinatorService;
    private final AlertService alertService;
    private final ExecutionStateService stateService;
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "autostone-retry"); t.setDaemon(true); return t;
    });

    /**
     * Entry point for both the SSH-wrapper callback and the local-exec watcher thread.
     * Marks this job's execution terminal, rolls up the parent box if this was its last
     * outstanding child, then walks the dependency graph to release anything now eligible.
     */
    @Transactional
    public boolean handleCompletion(UUID runId, Long jobId, Long executionId, int exitCode) {
        return handleCompletion(runId, jobId, executionId, exitCode, null);
    }

    @Transactional
    public boolean handleCompletion(UUID runId, Long jobId, Long executionId, int exitCode, String callbackStatus) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));

        JobExecution exec = executionId == null
                ? jobExecutionRepository.findFirstByJobIdAndRunIdOrderByIdDesc(jobId, runId)
                    .orElseThrow(() -> new NoSuchElementException("No execution for job " + jobId + " in run " + runId))
                : jobExecutionRepository.findById(executionId)
                    .orElseThrow(() -> new NoSuchElementException("Execution not found: " + executionId));
        if (!exec.getJob().getId().equals(jobId) || !runId.equals(exec.getRunId())) throw new IllegalArgumentException("Callback does not match execution");
        if (exec.getStatus() != JobStatus.RUNNING) { log.warn("Ignoring duplicate/stale callback for execution {} in status {}", exec.getId(), exec.getStatus()); return false; }

        if (callbackStatus != null && !callbackStatus.isBlank()) {
            try {
                JobStatus explicit = JobStatus.valueOf(callbackStatus.trim().toUpperCase(Locale.ROOT));
                if (explicit == JobStatus.SUCCESS || explicit == JobStatus.FAILED || explicit == JobStatus.TIMEOUT
                        || explicit == JobStatus.STOPPED || explicit == JobStatus.KILLED) {
                    stateService.transitionWithExitCode(exec, explicit, "Wrapper reported " + explicit, exitCode);
                    if (explicit != JobStatus.SUCCESS) alertService.onExecutionTerminal(exec);
                    executionCoordinatorService.dispatchNextQueued(job.getId());
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                log.warn("Ignoring unknown wrapper callback status '{}' for execution {}", callbackStatus, executionId);
            }
        }

        if (exitCode == 0) {
            exec.setExitCode(0);
            stateService.transitionWithExitCode(exec, JobStatus.SUCCESS, "Process completed successfully", 0);
            log.info("Job {} (run {}) finished successfully", job.getName(), runId);
            if (job.getBox() != null) rollUpBoxIfComplete(job.getBox(), exec.getWorkflowRunId());
            return true;
        }

        exec.setExitCode(exitCode);
        int maxRetries = job.getRetryCount() == null ? 0 : Math.max(0, job.getRetryCount());
        int attempt = exec.getAttempt() == null ? 1 : exec.getAttempt();
        if (attempt <= maxRetries) {
            stateService.transitionWithExitCode(exec, JobStatus.RETRY_WAIT, "Attempt " + attempt + " failed with exit code " + exitCode + "; retry scheduled", exitCode);
            int nextAttempt = attempt + 1;
            long delay = Math.max(1, job.getRetryIntervalSeconds() == null ? 60 : job.getRetryIntervalSeconds());
            retryExecutor.schedule(() -> retry(job.getId(), exec.getWorkflowRunId(), nextAttempt), delay, TimeUnit.SECONDS);
            return true;
        }

        stateService.transitionWithExitCode(exec, exitCode == 124 ? JobStatus.TIMEOUT : JobStatus.FAILED, exitCode == 124 ? "Execution timed out" : "Process exited with code " + exitCode, exitCode);
        alertService.onExecutionTerminal(exec);
        log.info("Job {} (run {}) finished with exit code {} -> {}", job.getName(), runId, exitCode, exec.getStatus());
        if (job.getBox() != null) rollUpBoxIfComplete(job.getBox(), exec.getWorkflowRunId());
        executionCoordinatorService.dispatchNextQueued(job.getId());
        return true;
    }


    private void retry(Long jobId, UUID workflowRunId, int nextAttempt) {
        try {
            Job job = jobRepository.findById(jobId).orElseThrow();
            int runNumber = jobExecutionRepository.findLatestForJob(job.getId()).size() + 1;
            JobExecution retry = JobExecution.builder().job(job).jobVersion(job.getVersion() == null ? 1 : job.getVersion())
                    .runNumber(runNumber).machineId(job.getMachineId()).runId(workflowRunId).workflowRunId(workflowRunId).status(JobStatus.QUEUED)
                    .attempt(nextAttempt).message("Retry attempt " + nextAttempt + " queued").build();
            retry = jobExecutionRepository.save(retry);
            if (!executionCoordinatorService.dispatchIfEligible(retry)) {
                // A held/iced/queued execution is intentionally left waiting. A transport
                // failure is already recorded as FAILED by dispatch(); do not overwrite a
                // legitimate WAITING/QUEUED state.
                if (retry.getStatus() == JobStatus.FAILED) {
                    releaseDownstream(workflowRunId, job);
                    executionCoordinatorService.dispatchNextQueued(job.getId());
                }
            }
        } catch (Exception e) { log.error("Retry failed for job {} workflow {}", jobId, workflowRunId, e); }
    }

    private void rollUpBoxIfComplete(Job box, UUID workflowRunId) {
        if (workflowRunId == null) return;
        stateService.recalculateBox(box, workflowRunId);
        stateService.recalculateAncestors(box, workflowRunId);
    }

    /**
     * Re-evaluates one waiting/halted execution after an external state change such as resume.
     * This is deliberately idempotent: only WAITING rows can be activated.
     */
    @Transactional(propagation = REQUIRES_NEW)
    public void tryActivate(UUID workflowRunId, Long jobId) {
        JobExecution exec = jobExecutionRepository
                .findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(jobId, workflowRunId)
                .orElse(null);
        if (exec == null || exec.getStatus() != JobStatus.WAITING) return;

        Job job = exec.getJob();
        if (stateService.isRuntimeHeld(exec) || Boolean.TRUE.equals(job.getHold()) || Boolean.TRUE.equals(job.getIce()) || Boolean.TRUE.equals(job.getNoExecution())) {
            return;
        }
        String expression = buildEffectiveExpression(job);
        try {
            if (expression == null || expression.isBlank()) {
                activateJob(job, workflowRunId, exec);
            } else if (ConditionEvaluator.evaluate(expression, name -> lookupOutcome(name, workflowRunId))) {
                activateJob(job, workflowRunId, exec);
            }
        } catch (IllegalArgumentException e) {
            log.error("Cannot evaluate dependency for job {}: {}", job.getName(), e.getMessage());
        }
    }

    /**
     * Terminal state changes are the single trigger for downstream dependency release.
     * AFTER_COMMIT prevents a downstream dispatch from racing the transaction that persisted
     * the completed state, and it also covers BOX -> SUCCESS/FAILED transitions derived from children.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void onExecutionStateChanged(ExecutionStateChangedEvent event) {
        if (event.newStatus() != JobStatus.SUCCESS && event.newStatus() != JobStatus.FAILED
                && event.newStatus() != JobStatus.TIMEOUT && event.newStatus() != JobStatus.SKIPPED) return;
        Job job = jobRepository.findById(event.jobId()).orElse(null);
        if (job == null || event.workflowRunId() == null) return;
        try {
            releaseDownstream(event.workflowRunId(), job);
        } catch (Exception ex) {
            log.error("Failed to release downstream jobs after execution {} became {}: {}",
                    event.executionId(), event.newStatus(), ex.getMessage(), ex);
        }
    }

    private void releaseDownstream(UUID workflowRunId, Job completedJob) {
        if (workflowRunId == null || completedJob == null) return;

        // Walk through terminal SKIPPED nodes as well as directly waiting nodes.
        // This is required for a chain such as A -> B -> C when B was skipped
        // while A was still running: A's later SUCCESS must re-check B's own
        // prerequisites and, once resolved, continue on to C even though B has
        // no second state transition.
        Deque<Job> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(completedJob);

        while (!queue.isEmpty()) {
            Job source = queue.removeFirst();
            if (!visited.add(source.getId())) continue;

            List<JobDependency> dependents = jobDependencyRepository.findByDependsOnJobId(source.getId());
            for (JobDependency dep : dependents) {
                Job child = dep.getJob();

                JobExecution childExec = jobExecutionRepository
                        .findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(child.getId(), workflowRunId)
                        .orElse(null);

                if (childExec == null) {
                    childExec = createDependencyExecution(child, source, workflowRunId);
                    log.info("Created downstream execution {} for job {} in workflow {} after dependency job {}",
                            childExec.getId(), child.getName(), workflowRunId, source.getName());
                }

                if (childExec.getStatus() == JobStatus.WAITING) {
                    boolean eligible = isConditionSatisfied(child, workflowRunId);
                    if (!eligible) {
                        childExec.setMessage("Waiting for dependency condition: " + buildEffectiveExpression(child));
                        jobExecutionRepository.save(childExec);
                        continue;
                    }
                    activateJob(child, workflowRunId, childExec);
                    continue;
                }

                // SKIPPED is terminal, but may represent a job that was skipped
                // before its prerequisites had completed. Once its prerequisites
                // are resolved, walk through it without changing its terminal state.
                if (childExec.getStatus() == JobStatus.SKIPPED && isConditionSatisfied(child, workflowRunId)) {
                    queue.addLast(child);
                }
            }
        }
    }

    private boolean isConditionSatisfied(Job job, UUID workflowRunId) {
        String expression = buildEffectiveExpression(job);
        if (expression == null || expression.isBlank()) return true;
        try {
            return ConditionEvaluator.evaluate(expression, name -> lookupOutcome(name, workflowRunId));
        } catch (IllegalArgumentException badExpr) {
            log.error("Job {} has an invalid condition expression '{}': {}", job.getName(), expression, badExpr.getMessage());
            return false;
        }
    }


    /**
     * Creates the execution row that AutoSys would materialize for a downstream dependency.
     * The child always receives a fresh run_id; workflow_run_id keeps it associated with the
     * same BOX/workflow execution when applicable.
     */
    private JobExecution createDependencyExecution(Job child, Job completedJob, UUID workflowRunId) {
        int runNumber = jobExecutionRepository.findLatestForJob(child.getId()).size() + 1;
        Long boxRunId = null;
        JobExecution completedExec = jobExecutionRepository
                .findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(completedJob.getId(), workflowRunId)
                .orElse(null);
        if (completedExec != null) boxRunId = completedExec.getBoxRunId();

        JobExecution execution = JobExecution.builder()
                .job(child)
                .jobVersion(child.getVersion() == null ? 1 : child.getVersion())
                .runNumber(runNumber)
                .machineId(child.getMachineId())
                .boxRunId(boxRunId)
                .runId(workflowRunId)
                .workflowRunId(workflowRunId)
                .status(JobStatus.CREATED)
                .attempt(1)
                .message("Created from satisfied dependency on " + completedJob.getName())
                .build();
        execution = jobExecutionRepository.saveAndFlush(execution);

        if (Boolean.TRUE.equals(child.getAutoHold())) {
            execution = stateService.transition(execution, JobStatus.WAITING,
                    "On Hold - AutoSys auto_hold is enabled");
            stateService.setRuntimeHold(execution, true, "AUTO_HOLD");
        } else {
            execution = stateService.transition(execution, JobStatus.WAITING,
                    "Waiting for starting conditions");
        }
        return execution;
    }

    /**
     * Turns a WAITING execution into RUNNING. For a plain JOB this means an actual dispatch.
     * For a BOX this has no command of its own to run - it means pre-creating WAITING rows
     * for its children (mirroring what EventService.start() does for a manually-started box)
     * and recursively activating whichever of those children have no dependency inside the box.
     */
    private void activateJob(Job job, UUID workflowRunId, JobExecution exec) {
        if (job.getJobType() == JobType.BOX) {
            stateService.transition(exec, JobStatus.RUNNING, "BOX execution activated");

            for (Job child : jobRepository.findByBoxId(job.getId())) {
                if (jobExecutionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(child.getId(), workflowRunId).isEmpty()) {
                    JobExecution childExec = executionCoordinatorService.createBoxChildExecution(child, exec.getId(), workflowRunId);
                        }
            }
            // Do not use a DB-only "root" query here: a child can have an external
            // dependency and still be the first runnable child inside the BOX.
            for (Job child : jobRepository.findByBoxId(job.getId())) {
                tryActivate(workflowRunId, child.getId());
            }
            stateService.recalculateBox(job, workflowRunId);
            stateService.recalculateAncestors(job, workflowRunId);
            return;
        }

        stateService.transition(exec, JobStatus.QUEUED, "Dependency satisfied; queued for execution");
        if (!executionCoordinatorService.dispatchIfEligible(exec)) {
            log.info("Downstream job {} remains queued because another execution is active", job.getName());
        }
    }

    /** Explicit condition wins; otherwise default to "all direct parents succeeded" (AND of s(parent)). */
    private String buildEffectiveExpression(Job job) {
        if (job.getConditionExpression() != null && !job.getConditionExpression().isBlank()) {
            return job.getConditionExpression();
        }
        List<String> parentNames = jobDependencyRepository.findByJobId(job.getId()).stream()
                .map(JobDependency::getDependsOnJob)
                .map(Job::getName)
                .toList();
        if (parentNames.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parentNames.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append("s(").append(parentNames.get(i)).append(")");
        }
        return sb.toString();
    }

    private boolean isKnownTerminal(Job job, UUID workflowRunId) {
        JobExecution e = jobExecutionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(job.getId(), workflowRunId).orElse(null);
        if (e == null) e = jobExecutionRepository.findFirstByJobIdOrderByIdDesc(job.getId()).orElse(null);
        return e != null && isTerminal(e.getStatus());
    }

    /** Operator SKIP is terminal and intentionally releases downstream success dependencies. */
    @Transactional
    public void releaseDownstreamAfterOperatorSkip(UUID workflowRunId, Job skippedJob) {
        if (workflowRunId == null || skippedJob == null) return;
        releaseDownstream(workflowRunId, skippedJob);
    }

    /**
     * Resolve a condition reference using the current workflow run first.
     *
     * SKIPPED is terminal for the skipped job itself, but it must not bypass an
     * unresolved prerequisite of that skipped job. For A -> B -> C, if A is
     * RUNNING and B is explicitly SKIPPED, s(B) remains PENDING for C. After A
     * succeeds, B's own condition is resolved and s(B) becomes SUCCESS.
     */
    private ConditionEvaluator.Outcome lookupOutcome(String jobName, UUID workflowRunId) {
        return lookupOutcome(jobName, workflowRunId, new HashSet<>());
    }

    private ConditionEvaluator.Outcome lookupOutcome(String jobName, UUID workflowRunId, Set<Long> resolving) {
        return jobRepository.findByName(jobName).map(job -> {
            JobExecution current = jobExecutionRepository
                    .findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(job.getId(), workflowRunId)
                    .orElse(null);
            JobExecution e = current != null
                    ? current
                    : jobExecutionRepository.findFirstByJobIdOrderByIdDesc(job.getId()).orElse(null);
            if (e == null) return ConditionEvaluator.Outcome.PENDING;

            if (e.getStatus() == JobStatus.SKIPPED) {
                if (!resolving.add(job.getId())) return ConditionEvaluator.Outcome.PENDING;
                String ownExpression = buildEffectiveExpression(job);
                boolean ownResolved = ownExpression == null || ownExpression.isBlank();
                if (!ownResolved) {
                    try {
                        ownResolved = ConditionEvaluator.evaluate(
                                ownExpression,
                                name -> lookupOutcome(name, workflowRunId, resolving));
                    } catch (IllegalArgumentException ex) {
                        ownResolved = false;
                    }
                }
                resolving.remove(job.getId());
                return ownResolved ? ConditionEvaluator.Outcome.SUCCESS : ConditionEvaluator.Outcome.PENDING;
            }

            return switch (e.getStatus()) {
                case SUCCESS -> ConditionEvaluator.Outcome.SUCCESS;
                case FAILED, STOPPED, TIMEOUT, KILLED, CANCELLED -> ConditionEvaluator.Outcome.FAILED;
                default -> ConditionEvaluator.Outcome.PENDING;
            };
        }).orElse(ConditionEvaluator.Outcome.PENDING);
    }

    private boolean isTerminal(JobStatus status) {
        return status == JobStatus.SUCCESS || status == JobStatus.FAILED || status == JobStatus.STOPPED || status == JobStatus.KILLED || status == JobStatus.TIMEOUT || status == JobStatus.SKIPPED || status == JobStatus.CANCELLED;
    }
}