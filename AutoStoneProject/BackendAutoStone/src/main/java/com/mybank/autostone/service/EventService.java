package com.mybank.autostone.service;

import com.mybank.autostone.dto.EventActionRequest;
import com.mybank.autostone.dto.EventResponse;
import com.mybank.autostone.entity.*;
import com.mybank.autostone.remote.RemoteSignal;
import com.mybank.autostone.remote.RemoteCommandResult;
import com.mybank.autostone.remote.RemoteExecutionService;
import com.mybank.autostone.repository.JobDependencyRepository;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import com.mybank.autostone.scheduler.QuartzSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private static final List<JobStatus> ACTIVE_STATUSES =
            List.of(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.WAITING, JobStatus.PAUSED, JobStatus.RETRY_WAIT);

    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final QuartzSchedulerService quartzSchedulerService;
    private final ExecutionCoordinatorService executionCoordinatorService;
    private final RemoteExecutionService remoteExecutionService;
    private final DependencyResolverService dependencyResolverService;
    private final com.mybank.autostone.repository.EventQueueRepository eventQueueRepository;
    private final ExecutionStateService stateService;

    // ---------------------------------------------------------------
    // START
    // ---------------------------------------------------------------
    public EventResponse start(Long jobId, EventActionRequest request) {
        Job job = getJob(jobId);
        recordEvent("STARTJOB", job, request);
        Optional<JobExecution> active = executionCoordinatorService.activeExecution(jobId);
        ConcurrencyPolicy policy = job.getConcurrencyPolicy() == null ? ConcurrencyPolicy.ALLOW : job.getConcurrencyPolicy();
        if (job.getJobType() == JobType.BOX && active.isPresent() && policy == ConcurrencyPolicy.DISALLOW) {
            throw new IllegalStateException("BOX is already active: " + job.getName());
        }
        if (job.getJobType() == JobType.BOX) return startBox(job);
        if (active.isPresent() && policy == ConcurrencyPolicy.DISALLOW) {
            throw new IllegalStateException("Job is already active: " + job.getName());
        }

        UUID runId = UUID.randomUUID();
        JobExecution exec = executionCoordinatorService.createQueuedExecution(job, runId, 1);

        if (Boolean.TRUE.equals(job.getHold()) || Boolean.TRUE.equals(job.getIce()) || Boolean.TRUE.equals(job.getNoExecution())) {
            JobStatus blocked = JobStatus.WAITING;
            stateService.transition(exec, blocked, Boolean.TRUE.equals(job.getHold()) ? "On Hold" :
                    Boolean.TRUE.equals(job.getIce()) ? "Waiting - job is on ice" : "Waiting - no-execution mode is enabled");
            if (Boolean.TRUE.equals(job.getHold())) stateService.setRuntimeHold(exec, true, "DEFINITION_HOLD");
            jobExecutionRepository.save(exec);
            return buildResponse(job, runId, blocked, exec.getMessage(), List.of());
        }

        // A manually requested dependency job is not allowed to bypass its starting
        // condition. It is created in WAITING and the dependency engine decides when it runs.
        if (hasDependencies(job)) {
            stateService.transition(exec, JobStatus.WAITING, "Waiting for starting conditions");
            jobExecutionRepository.save(exec);
            dependencyResolverService.tryActivate(runId, job.getId());
            JobStatus resulting = jobExecutionRepository.findById(exec.getId()).map(JobExecution::getStatus).orElse(JobStatus.WAITING);
            return buildResponse(job, runId, resulting, resulting == JobStatus.RUNNING ? "Starting condition satisfied; job started" : "Waiting for starting conditions", List.of());
        }

        if (policy == ConcurrencyPolicy.QUEUE && active.isPresent()) {
            exec.setMessage("Queued behind execution " + active.get().getId());
            jobExecutionRepository.save(exec);
            return buildResponse(job, runId, JobStatus.QUEUED, "Job queued behind the active execution", List.of());
        }

        boolean dispatched = executionCoordinatorService.dispatchIfEligible(exec);
        return buildResponse(job, runId, dispatched ? JobStatus.RUNNING : JobStatus.FAILED,
                dispatched ? "Job started" : "Job failed to dispatch", List.of());
    }

    private EventResponse startBox(Job job) {
        UUID runId = UUID.randomUUID();
        JobExecution boxExec = executionCoordinatorService.createQueuedExecution(job, runId, 1);
        if (Boolean.TRUE.equals(job.getHold()) || Boolean.TRUE.equals(job.getIce()) || Boolean.TRUE.equals(job.getNoExecution())) {
            JobStatus blocked = JobStatus.WAITING;
            stateService.transition(boxExec, blocked, Boolean.TRUE.equals(job.getHold()) ? "BOX on hold" :
                    Boolean.TRUE.equals(job.getIce()) ? "BOX waiting - on ice" : "BOX waiting - no-execution mode");
            if (Boolean.TRUE.equals(job.getHold())) stateService.setRuntimeHold(boxExec, true, "DEFINITION_HOLD");
            jobExecutionRepository.save(boxExec);
            return buildResponse(job, runId, blocked, boxExec.getMessage(), List.of());
        }

        stateService.transition(boxExec, JobStatus.RUNNING, "Box execution started");

        List<Job> children = jobRepository.findByBoxId(job.getId());
        if (children.isEmpty()) {
            stateService.transition(boxExec, JobStatus.SUCCESS, "Empty box completed successfully");
            return buildResponse(job, runId, JobStatus.SUCCESS, "Empty box completed successfully", List.of());
        }

        for (Job child : children) {
            // auto_hold affects only this runtime execution; never mutate Job.hold.
            JobExecution childExec = executionCoordinatorService.createBoxChildExecution(child, boxExec.getId(), runId);
        }

        List<Long> affected = new ArrayList<>();
        // Evaluate every child, not only DB "roots". This supports external dependencies
        // and condition expressions while still allowing dependency-free roots to start.
        for (Job child : children) {
            dependencyResolverService.tryActivate(runId, child.getId());
            affected.add(child.getId());
        }
        stateService.recalculateBox(job, runId);
        return buildResponse(job, runId, jobExecutionRepository.findById(boxExec.getId()).map(JobExecution::getStatus).orElse(JobStatus.WAITING), "Box started", affected);
    }

    // ---------------------------------------------------------------
    // STOP
    // ---------------------------------------------------------------
    @org.springframework.transaction.annotation.Transactional
    public EventResponse stop(Long jobId, EventActionRequest request) {
        Job job = getJob(jobId);
        recordEvent("STOPJOB", job, request);
        JobExecution activeExec = getActiveExecution(job);
        UUID runId = activeExec.getRunId();
        List<Long> affected = new ArrayList<>();

        if (activeExec.getStatus() != JobStatus.RUNNING && activeExec.getStatus() != JobStatus.PAUSED) {
            throw new IllegalStateException("Stop is allowed only for RUNNING or PAUSED executions");
        }

        if (job.getJobType() == JobType.BOX) {
            for (JobExecution childExec : executionsForBoxTree(job, activeExec.getWorkflowRunId())) {
                if (childExec.getJob().getId().equals(job.getId()) || isTerminal(childExec.getStatus())) continue;
                if ((childExec.getStatus() == JobStatus.RUNNING || childExec.getStatus() == JobStatus.PAUSED)
                        && childExec.getJob().getJobType() != JobType.BOX) signalAndLog(childExec, RemoteSignal.STOP);
                stateService.transitionOperator(childExec, JobStatus.STOPPED, "Stopped with BOX " + job.getName()); affected.add(childExec.getJob().getId());
            }
            stateService.transitionOperator(activeExec, JobStatus.STOPPED, "BOX stopped by operator");
            stateService.recalculateAncestors(job, activeExec.getWorkflowRunId());
        } else {
            if (activeExec.getStatus() == JobStatus.RUNNING || activeExec.getStatus() == JobStatus.PAUSED) signalAndLog(activeExec, RemoteSignal.STOP);
            stateService.transition(activeExec, JobStatus.STOPPED, "Stopped by operator");
        }
        executionCoordinatorService.dispatchNextQueued(jobId);
        return buildResponse(job, runId, JobStatus.STOPPED, "Job stopped", affected);
    }

    // ---------------------------------------------------------------
    // PAUSE
    // ---------------------------------------------------------------
    @org.springframework.transaction.annotation.Transactional
    public EventResponse pause(Long jobId, EventActionRequest request) {
        recordEvent("PAUSEJOB", getJob(jobId), request);
        Job job = getJob(jobId);
        JobExecution activeExec = getActiveExecution(job);
        UUID runId = activeExec.getRunId();
        List<Long> affected = new ArrayList<>();
        if (activeExec.getStatus() != JobStatus.RUNNING) {
            throw new IllegalStateException("Only a running execution can be paused");
        }

        if (job.getJobType() == JobType.BOX) {
            List<JobExecution> runningChildren = executionsForBoxTree(job, activeExec.getWorkflowRunId()).stream()
                    .filter(e -> e.getStatus() == JobStatus.RUNNING).toList();

            for (JobExecution childExec : runningChildren) {
                if (childExec.getJob().getId().equals(job.getId())) continue;
                if (childExec.getJob().getJobType() != JobType.BOX) signalAndLog(childExec, RemoteSignal.PAUSE);
                stateService.transition(childExec, JobStatus.PAUSED, "Paused with BOX");
                affected.add(childExec.getJob().getId());
            }
            stateService.transition(activeExec, JobStatus.PAUSED, "BOX paused");
        } else {
            signalAndLog(activeExec, RemoteSignal.PAUSE);
            stateService.transition(activeExec, JobStatus.PAUSED, "Job paused");

        }

        return buildResponse(job, runId, JobStatus.PAUSED, "Job paused", affected);
    }

    // ---------------------------------------------------------------
    // RESUME
    // ---------------------------------------------------------------
    @org.springframework.transaction.annotation.Transactional
    public EventResponse resume(Long jobId, EventActionRequest request) {
        recordEvent("RESUMEJOB", getJob(jobId), request);
        Job job = getJob(jobId);
        JobExecution pausedExec = jobExecutionRepository
                .findFirstByJobIdAndStatusInOrderByIdDesc(job.getId(), List.of(JobStatus.PAUSED))
                .orElseThrow(() -> new IllegalStateException("No paused execution found for job " + jobId));

        UUID runId = pausedExec.getRunId();
        // The OS process is only suspended (kill -STOP); resume un-suspends the same PID.
        // For a BOX, resume every paused child in the same run as well.
        if (job.getJobType() == JobType.BOX) {
            for (JobExecution child : executionsForBoxTree(job, pausedExec.getWorkflowRunId()).stream().filter(e -> e.getStatus() == JobStatus.PAUSED).toList()) {
                if (child.getJob().getId().equals(job.getId())) continue;
                stateService.setRuntimeHold(child, false, null);
                if (child.getJob().getJobType() == JobType.BOX) {
                    // BOX executions are orchestration records and have no OS PID.
                    // Resume their lifecycle state; their executable descendants are
                    // resumed separately in this same run.
                    stateService.transition(child, JobStatus.RUNNING, "BOX resumed");
                } else if (child.getPid() != null) {
                    signalAndLog(child, RemoteSignal.RESUME);
                    stateService.transition(child, JobStatus.RUNNING, "Resumed with BOX");
                } else {
                    stateService.transition(child, JobStatus.WAITING, "BOX resumed; re-evaluating dependencies");
                    dependencyResolverService.tryActivate(pausedExec.getWorkflowRunId(), child.getJob().getId());
                }
            }
            stateService.setRuntimeHold(pausedExec, false, null);
            stateService.transition(pausedExec, JobStatus.RUNNING, "BOX resumed");
        } else {
            stateService.setRuntimeHold(pausedExec, false, null);
            if (pausedExec.getPid() != null) {
                signalAndLog(pausedExec, RemoteSignal.RESUME);
                stateService.transition(pausedExec, JobStatus.RUNNING, "Job resumed");
            } else {
                stateService.transition(pausedExec, JobStatus.WAITING, "Job hold released; re-evaluating dependencies");
                dependencyResolverService.tryActivate(pausedExec.getWorkflowRunId(), jobId);
            }
        }

        // Pause/resume controls only the selected running execution. Downstream jobs
        // remain WAITING and are released normally when their dependency succeeds.
        return buildResponse(job, runId, JobStatus.RUNNING, "Job resumed", List.of());
    }

    // ---------------------------------------------------------------
    // RESTART - fresh run_id, does not touch history of the old run
    // ---------------------------------------------------------------
    @org.springframework.transaction.annotation.Transactional
    public EventResponse restart(Long jobId, EventActionRequest request) {
        recordEvent("RERUNJOB", getJob(jobId), request);
        Job job = getJob(jobId);
        if (executionCoordinatorService.activeExecution(jobId).isPresent()) {
            throw new IllegalStateException("Restart is allowed only after the current execution is no longer active");
        }
        return start(job.getId(), request); // reuses start() logic with a brand new run_id
    }

    // ---------------------------------------------------------------
    // AUTOSYS MONITORING CONTROLS
    // ---------------------------------------------------------------
    @org.springframework.transaction.annotation.Transactional
    public EventResponse forceStart(Long jobId, EventActionRequest request) {
        recordEvent("FORCE_STARTJOB", getJob(jobId), request);
        Job job = getJob(jobId);
        if (Boolean.TRUE.equals(job.getHold()) || Boolean.TRUE.equals(job.getIce()) || Boolean.TRUE.equals(job.getNoExecution())) {
            throw new IllegalStateException("Cannot force start " + job.getName() + " while Hold/Ice/No-Execution is enabled");
        }
        if (job.getJobType() == JobType.BOX) {
            if (executionCoordinatorService.activeExecution(jobId).isPresent() && job.getConcurrencyPolicy() == ConcurrencyPolicy.DISALLOW)
                throw new IllegalStateException("BOX is already active: " + job.getName());
            return startBox(job);
        }
        UUID runId = UUID.randomUUID();
        JobExecution exec = executionCoordinatorService.createQueuedExecution(job, runId, 1);
        boolean dispatched = executionCoordinatorService.dispatch(exec);
        return buildResponse(job, runId, dispatched ? JobStatus.RUNNING : JobStatus.FAILED,
                dispatched ? "Job force started" : "Force start failed", List.of());
    }

    @org.springframework.transaction.annotation.Transactional
    public EventResponse setHold(Long jobId, boolean value) {
        Job job = getJob(jobId);
        recordEvent("JOB_HOLD_CHANGE", job, null);

        Optional<JobExecution> active = jobExecutionRepository
                .findFirstByJobIdAndStatusInOrderByIdDesc(jobId, ACTIVE_STATUSES);

        // HOLD is a scheduler/orchestration control for work that has not started.
        // It is intentionally different from PAUSE. A live or paused OS process must
        // be controlled only with pause/resume/stop/kill.
        if (value && active.isPresent()
                && (active.get().getStatus() == JobStatus.RUNNING || active.get().getStatus() == JobStatus.PAUSED)) {
            throw new IllegalStateException("Cannot HOLD a RUNNING/PAUSED execution. Use Pause, Resume, Stop or Kill.");
        }

        List<Long> affected = new ArrayList<>();
        applyHoldRecursively(job, value, affected);

        if (value) {
            active.ifPresent(parentExec -> {
                List<JobExecution> scope = job.getJobType() == JobType.BOX
                        ? executionsForBoxTree(job, parentExec.getWorkflowRunId()) : List.of(parentExec);
                for (JobExecution exec : scope) {
                    if (exec.getStatus() == JobStatus.RUNNING || exec.getStatus() == JobStatus.PAUSED) continue;
                    if (exec.getStatus() == JobStatus.QUEUED) {
                        stateService.transition(exec, JobStatus.WAITING, "Execution placed on hold by operator");
                    } else if (exec.getStatus() == JobStatus.WAITING) {
                        exec.setMessage("Execution placed on hold by operator");
                        jobExecutionRepository.save(exec);
                    }
                    if (exec.getStatus() == JobStatus.WAITING || exec.getStatus() == JobStatus.QUEUED) {
                        stateService.setRuntimeHold(exec, true, "OPERATOR_HOLD");
                        affected.add(exec.getJob().getId());
                    }
                }
            });
        } else {
            active.ifPresent(parentExec -> {
                List<JobExecution> scope = job.getJobType() == JobType.BOX
                        ? executionsForBoxTree(job, parentExec.getWorkflowRunId()) : List.of(parentExec);
                for (JobExecution exec : scope) {
                    if (!Boolean.TRUE.equals(exec.getRuntimeHold())) continue;
                    stateService.setRuntimeHold(exec, false, null);
                    if (exec.getStatus() == JobStatus.WAITING || exec.getStatus() == JobStatus.HALTED) {
                        if (exec.getStatus() == JobStatus.HALTED) {
                            stateService.transition(exec, JobStatus.WAITING, "Hold released; re-evaluating dependencies");
                        }
                        dependencyResolverService.tryActivate(exec.getWorkflowRunId(), exec.getJob().getId());
                    }
                    affected.add(exec.getJob().getId());
                }
            });
            wakeWaiting(job);
        }
        return buildResponse(job, latestRunId(job), latestStatus(job), value ? "On Hold" : "Off Hold", affected);
    }

    private void applyHoldRecursively(Job job, boolean value, List<Long> affected) {
        job.setHold(value);
        jobRepository.save(job);
        affected.add(job.getId());
        if (job.getJobType() == JobType.BOX) {
            for (Job child : jobRepository.findByBoxId(job.getId())) {
                applyHoldRecursively(child, value, affected);
            }
        }
    }

    /** Mark the latest/current execution as SKIPPED without deleting history. */
    public EventResponse skip(Long jobId, EventActionRequest request) {
        Job job = getJob(jobId);
        recordEvent("SKIPJOB", job, request);
        JobExecution exec = jobExecutionRepository.findFirstByJobIdAndStatusInOrderByIdDesc(jobId, ACTIVE_STATUSES).orElse(null);
        if (exec != null && exec.getStatus() != JobStatus.WAITING && exec.getStatus() != JobStatus.QUEUED) {
            throw new IllegalStateException("Skip is allowed only for WAITING or QUEUED executions");
        }
        if (exec == null) {
            exec = JobExecution.builder().job(job).jobVersion(job.getVersion() == null ? 1 : job.getVersion())
                    .runId(UUID.randomUUID()).runNumber(1).attempt(1).status(JobStatus.SKIPPED)
                    .startTime(LocalDateTime.now()).endTime(LocalDateTime.now())
                    .message("Skipped by operator").build();
        } else {
            stateService.transition(exec, JobStatus.SKIPPED, "Skipped by operator");
        }
        jobExecutionRepository.save(exec);
        UUID workflowRunId = exec.getWorkflowRunId() != null ? exec.getWorkflowRunId() : exec.getRunId();
        if (job.getBox() != null && workflowRunId != null) rollUpBoxAfterOperatorChange(job.getBox(), workflowRunId);
        // Explicit operator SKIP is a terminal dependency outcome. Immediately re-evaluate
        // dependents in the same workflow so A -> B -> C can continue when B is skipped.
        if (workflowRunId != null) dependencyResolverService.releaseDownstreamAfterOperatorSkip(workflowRunId, job);
        return buildResponse(job, exec.getRunId(), JobStatus.SKIPPED, "Job skipped", List.of());
    }

    private void rollUpBoxAfterOperatorChange(Job box, UUID runId) {
        stateService.recalculateBox(box, runId);
        stateService.recalculateAncestors(box, runId);
    }

    public EventResponse setIce(Long jobId, boolean value) {
        recordEvent("JOB_ICE_CHANGE", getJob(jobId), null);
        Job job = getJob(jobId);
        job.setIce(value);
        jobRepository.save(job);
        if (!value) wakeWaiting(job);
        return buildResponse(job, latestRunId(job), latestStatus(job), value ? "On Ice" : "Off Ice", List.of());
    }

    public EventResponse setNoExecution(Long jobId, boolean value) {
        recordEvent("JOB_NO_EXECUTION_CHANGE", getJob(jobId), null);
        Job job = getJob(jobId);
        job.setNoExecution(value);
        jobRepository.save(job);
        if (!value) wakeWaiting(job);
        return buildResponse(job, latestRunId(job), latestStatus(job), value ? "No Execution enabled" : "No Execution disabled", List.of());
    }

    public EventResponse releaseResources(Long jobId) {
        recordEvent("RELEASE_RESOURCES", getJob(jobId), null);
        Job job = getJob(jobId);
        Optional<JobExecution> active = jobExecutionRepository.findFirstByJobIdAndStatusInOrderByIdDesc(jobId, ACTIVE_STATUSES);
        if (active.isPresent()) {
            active.get().setMessage("Resources released by operator");
            jobExecutionRepository.save(active.get());
        }
        return buildResponse(job, active.map(JobExecution::getRunId).orElse(latestRunId(job)),
                active.map(JobExecution::getStatus).orElse(latestStatus(job)), "Resources released", List.of());
    }

    @org.springframework.transaction.annotation.Transactional
    public EventResponse kill(Long jobId, EventActionRequest request) {
        Job job = getJob(jobId);
        recordEvent("KILLJOB", job, request);
        JobExecution activeExec = getActiveExecution(job);
        UUID runId = activeExec.getRunId();
        List<Long> affected = new ArrayList<>();
        if (activeExec.getStatus() != JobStatus.RUNNING && activeExec.getStatus() != JobStatus.PAUSED) {
            throw new IllegalStateException("Kill is allowed only for RUNNING or PAUSED executions");
        }
        if (job.getJobType() == JobType.BOX) {
            List<JobExecution> runExecutions = executionsForBoxTree(job, activeExec.getWorkflowRunId());
            // First signal every live process, then persist KILLED deepest-to-shallowest
            // without intermediate BOX roll-ups. This prevents a nested BOX becoming FAILED
            // before the operator's explicit KILL reaches it.
            for (JobExecution child : runExecutions) {
                if (child.getJob().getId().equals(job.getId()) || isTerminal(child.getStatus())) continue;
                if ((child.getStatus() == JobStatus.RUNNING || child.getStatus() == JobStatus.PAUSED)
                        && child.getJob().getJobType() != JobType.BOX) signalAndLog(child, RemoteSignal.KILL);
            }
            runExecutions.stream()
                    .filter(e -> !e.getJob().getId().equals(job.getId()) && !isTerminal(e.getStatus()))
                    .sorted(Comparator.comparingInt((JobExecution e) -> boxDepth(e.getJob())).reversed())
                    .forEach(e -> {
                        stateService.transitionOperator(
                                e,
                                JobStatus.KILLED,
                                "Killed with BOX " + job.getName()
                        );
                        affected.add(e.getJob().getId());
                    });
        } else {
            if (activeExec.getStatus() == JobStatus.RUNNING || activeExec.getStatus() == JobStatus.PAUSED) signalAndLog(activeExec, RemoteSignal.KILL);
        }
        if (job.getJobType() == JobType.BOX) {
            stateService.transitionOperator(activeExec, JobStatus.KILLED, "BOX killed by operator");
            stateService.recalculateAncestors(job, activeExec.getWorkflowRunId());
        } else {
            stateService.transition(activeExec, JobStatus.KILLED, "Killed by operator");
        }
        return buildResponse(job, runId, JobStatus.KILLED, "Job killed", affected);
    }

    public EventResponse sendEvent(Long jobId, EventActionRequest request) {
        recordEvent("SEND_EVENT", getJob(jobId), request);
        Job job = getJob(jobId);
        String event = request != null && request.getEvent() != null && !request.getEvent().isBlank()
                ? request.getEvent().trim() : "OPERATOR_EVENT";
        Optional<JobExecution> active = jobExecutionRepository.findFirstByJobIdAndStatusInOrderByIdDesc(jobId, ACTIVE_STATUSES);
        if (active.isEmpty() && job.getTriggerType() == TriggerType.EVENT) {
            return start(jobId, request);
        }
        active.ifPresent(e -> { e.setMessage("Event: " + event); jobExecutionRepository.save(e); });
        return buildResponse(job, active.map(JobExecution::getRunId).orElse(latestRunId(job)),
                active.map(JobExecution::getStatus).orElse(latestStatus(job)), "Event sent: " + event, List.of());
    }

    @org.springframework.transaction.annotation.Transactional
    public EventResponse cancelEvent(Long jobId) {
        Job job = getJob(jobId);
        recordEvent("CANCEL_EVENT", job, null);
        Optional<JobExecution> active = jobExecutionRepository.findFirstByJobIdAndStatusInOrderByIdDesc(jobId, ACTIVE_STATUSES);
        if (active.isEmpty()) return buildResponse(job, latestRunId(job), latestStatus(job), "No active event to cancel", List.of());
        JobExecution exec = active.get();
        UUID runId = exec.getRunId();
        List<Long> affected = new ArrayList<>();
        if (job.getJobType() == JobType.BOX) {
            for (JobExecution child : executionsForBoxTree(job, exec.getWorkflowRunId())) {
                if (child.getJob().getId().equals(job.getId()) || isTerminal(child.getStatus())) continue;
                if ((child.getStatus() == JobStatus.RUNNING || child.getStatus() == JobStatus.PAUSED)
                        && child.getJob().getJobType() != JobType.BOX) signalAndLog(child, RemoteSignal.STOP);
                stateService.transitionOperator(child, JobStatus.CANCELLED, "Cancelled with BOX " + job.getName()); affected.add(child.getJob().getId());
            }
            stateService.transitionOperator(exec, JobStatus.CANCELLED, "BOX event cancelled by operator");
            stateService.recalculateAncestors(job, exec.getWorkflowRunId());
            return buildResponse(job, runId, JobStatus.CANCELLED, "Event cancelled", affected);
        } else if (exec.getStatus() == JobStatus.RUNNING || exec.getStatus() == JobStatus.PAUSED) {
            signalAndLog(exec, RemoteSignal.STOP);
        }
        stateService.transition(exec, JobStatus.CANCELLED, "Event cancelled by operator");
        return buildResponse(job, runId, JobStatus.CANCELLED, "Event cancelled", affected);
    }

    public EventResponse markSuccess(Long jobId, EventActionRequest request) {
        return markTerminal(jobId, JobStatus.SUCCESS, request);
    }

    public EventResponse markFailure(Long jobId, EventActionRequest request) {
        return markTerminal(jobId, JobStatus.FAILED, request);
    }

    private EventResponse markTerminal(Long jobId, JobStatus target, EventActionRequest request) {
        Job job = getJob(jobId);
        recordEvent("MARK_" + target.name(), job, request);
        JobExecution exec = jobExecutionRepository.findFirstByJobIdOrderByIdDesc(jobId).orElse(null);
        if (exec == null || isTerminal(exec.getStatus())) {
            UUID runId = UUID.randomUUID();
            exec = JobExecution.builder().job(job).jobVersion(job.getVersion() == null ? 1 : job.getVersion())
                    .runId(runId).runNumber(1).attempt(1).status(JobStatus.CREATED).startTime(LocalDateTime.now())
                    .message("Created for operator status change").build();
            exec = jobExecutionRepository.save(exec);
            stateService.transition(exec, target, "Status marked " + target.name() + " by operator");
        } else {
            stateService.transition(exec, target, "Status marked " + target.name() + " by operator");
        }
        jobExecutionRepository.save(exec);
        if (job.getBox() != null) stateService.recalculateAncestors(job, exec.getWorkflowRunId());
        return buildResponse(job, exec.getRunId(), target, "Job marked " + target.name(), List.of());
    }

    private boolean isTerminal(JobStatus status) {
        return status == JobStatus.SUCCESS || status == JobStatus.FAILED || status == JobStatus.STOPPED || status == JobStatus.KILLED || status == JobStatus.TIMEOUT || status == JobStatus.SKIPPED || status == JobStatus.CANCELLED;
    }

    private void wakeWaiting(Job job) {
        jobExecutionRepository.findFirstByJobIdAndStatusInOrderByIdDesc(job.getId(),
                List.of(JobStatus.WAITING, JobStatus.QUEUED)).ifPresent(exec -> {
            if (exec.getStatus() == JobStatus.WAITING) dependencyResolverService.tryActivate(exec.getWorkflowRunId(), job.getId());
            else executionCoordinatorService.dispatchIfEligible(exec);
        });
    }

    private UUID latestRunId(Job job) {
        return jobExecutionRepository.findFirstByJobIdOrderByIdDesc(job.getId()).map(JobExecution::getRunId).orElse(null);
    }

    private JobStatus latestStatus(Job job) {
        return jobExecutionRepository.findFirstByJobIdOrderByIdDesc(job.getId()).map(JobExecution::getStatus).orElse(null);
    }

    // ---------------------------------------------------------------
    // SCHEDULE ENABLE / DISABLE - does not touch JobExecution at all
    // ---------------------------------------------------------------
    public EventResponse scheduleEnable(Long jobId) {
        recordEvent("SCHEDULE_ENABLE", getJob(jobId), null);
        Job job = getJob(jobId);
        job.setEnabled(true);
        jobRepository.save(job);
        if (job.getTriggerType() == TriggerType.CRON) {
            quartzSchedulerService.scheduleCronJob(job);
        }
        return EventResponse.builder()
                .jobId(job.getId()).jobName(job.getName())
                .message("Schedule enabled").timestamp(LocalDateTime.now())
                .build();
    }

    public EventResponse scheduleDisable(Long jobId) {
        recordEvent("SCHEDULE_DISABLE", getJob(jobId), null);
        Job job = getJob(jobId);
        job.setEnabled(false);
        jobRepository.save(job);
        if (job.getTriggerType() == TriggerType.CRON) {
            // unregisters the Quartz trigger only - any run already in flight is untouched,
            // this just stops the NEXT scheduled fire from happening
            quartzSchedulerService.unscheduleJob(job.getId());
        }
        return EventResponse.builder()
                .jobId(job.getId()).jobName(job.getName())
                .message("Schedule disabled - current run (if any) unaffected").timestamp(LocalDateTime.now())
                .build();
    }

    // =================================================================
    // Internal helpers
    // =================================================================
    private boolean hasDependencies(Job job) {
        return (job.getConditionExpression() != null && !job.getConditionExpression().isBlank())
                || !jobDependencyRepository.findByJobId(job.getId()).isEmpty();
    }

    private void recordEvent(String type, Job job, EventActionRequest request) {
        try {
            EventQueue event = EventQueue.builder().eventType(type).jobCode(job.getId()).priority(0)
                    .requestedBy(request != null && request.getTriggeredBy() != null ? request.getTriggeredBy() : "MANUAL")
                    .payload(request == null ? null : request.getEvent()).createTime(LocalDateTime.now()).build();
            // Persisting the command before executing it gives the DB a durable operator audit trail.
            eventQueueRepository.save(event);
        } catch (Exception e) {
            log.warn("Could not persist event {} for job {}: {}", type, job.getId(), e.getMessage());
        }
    }


    private Job getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
    }

    private JobExecution getActiveExecution(Job job) {
        return jobExecutionRepository
                .findFirstByJobIdAndStatusInOrderByIdDesc(job.getId(), ACTIVE_STATUSES)
                .orElseThrow(() -> new IllegalStateException("No active execution for job " + job.getId()));
    }

    /**
     * Walks the dependency graph downstream from fromJobId (BFS) and marks
     * any WAITING execution under the given run as HALTED.
     * Returns the ids of jobs that were halted.
     */
    private List<Long> haltDownstream(UUID runId, Long fromJobId) {
        List<Long> haltedIds = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(fromJobId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            List<JobDependency> children = jobDependencyRepository.findByDependsOnJobId(current);

            for (JobDependency dep : children) {
                Long childJobId = dep.getJob().getId();
                if (!visited.add(childJobId)) continue;

                jobExecutionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(childJobId, runId)
                        .filter(e -> e.getStatus() == JobStatus.WAITING)
                        .ifPresent(e -> {
                            stateService.transition(e, JobStatus.HALTED, "Downstream halted after operator action");
                            haltedIds.add(childJobId);
                        });

                queue.add(childJobId);
            }
        }
        return haltedIds;
    }

    /** If the job belongs to a box, mark the box's own execution HALTED. */
    private Optional<Long> haltParentBoxIfAny(Job job, UUID runId) {
        if (job.getBox() == null) return Optional.empty();

        return jobExecutionRepository
                .findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(job.getBox().getId(), runId)
                .filter(e -> e.getStatus() == JobStatus.RUNNING)
                .map(e -> {
                    stateService.transition(e, JobStatus.HALTED, "Downstream halted after operator action");
                    return job.getBox().getId();
                });
    }

    /** On resume, flip HALTED rows (in this run) back to WAITING so they re-enter play. */
    private List<Long> resetHaltedToWaiting(UUID runId) {
        List<Long> resetIds = new ArrayList<>();
        for (JobExecution exec : jobExecutionRepository.findByWorkflowRunId(runId)) {
            if (exec.getStatus() == JobStatus.HALTED) {
                stateService.transition(exec, JobStatus.WAITING, "Execution returned to waiting state");
                resetIds.add(exec.getJob().getId());
            }
        }
        return resetIds;
    }

    private void signalAndLog(JobExecution exec, RemoteSignal signal) {
        RemoteCommandResult result = remoteExecutionService.sendSignal(exec, signal);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Failed to send " + signal + " for execution " + exec.getId() + ": " + result.getErrorMessage());
        }
    }

    /** Returns only the executions belonging to this BOX and all nested descendants. */
    private List<JobExecution> executionsForBoxTree(Job box, UUID runId) {
        Set<Long> jobIds = new HashSet<>();
        Deque<Job> queue = new ArrayDeque<>();
        queue.add(box);
        while (!queue.isEmpty()) {
            Job current = queue.removeFirst();
            if (!jobIds.add(current.getId())) continue;
            if (current.getJobType() == JobType.BOX) queue.addAll(jobRepository.findByBoxId(current.getId()));
        }
        return jobExecutionRepository.findByWorkflowRunId(runId).stream()
                .filter(e -> e.getJob() != null && jobIds.contains(e.getJob().getId()))
                .toList();
    }

    private int boxDepth(Job job) {
        int depth = 0;
        Set<Long> seen = new HashSet<>();
        Job cursor = job;
        while (cursor != null && seen.add(cursor.getId())) {
            depth++;
            cursor = cursor.getBox();
        }
        return depth;
    }

    private EventResponse buildResponse(Job job, UUID runId, JobStatus status, String message, List<Long> affected) {
        return EventResponse.builder()
                .jobId(job.getId())
                .executionId(jobExecutionRepository.findFirstByJobIdAndRunIdOrderByIdDesc(job.getId(), runId).map(JobExecution::getId).orElse(null))
                .jobName(job.getName())
                .jobType(job.getJobType().name())
                .runId(runId)
                .status(status)
                .message(message)
                .timestamp(LocalDateTime.now())
                .affectedJobIds(affected)
                .affectedExecutionIds(runId == null ? List.of() : jobExecutionRepository.findFirstByJobIdAndRunIdOrderByIdDesc(job.getId(), runId)
                        .map(JobExecution::getWorkflowRunId)
                        .map(jobExecutionRepository::findByWorkflowRunId)
                        .orElse(List.of()).stream()
                        .filter(e -> affected.contains(e.getJob().getId())).map(JobExecution::getId).toList())
                .build();
    }
}