package com.mybank.autostone.service;

import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobRunEvent;
import com.mybank.autostone.entity.JobStatus;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import com.mybank.autostone.repository.JobRunEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Single authoritative execution-state transition and BOX roll-up service.
 * UI code never decides a BOX state; it is derived here from the current run's children.
 */
@Service
@RequiredArgsConstructor
public class ExecutionStateService {
    private final JobExecutionRepository executionRepository;
    private final JobRepository jobRepository;
    private final JobRunEventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED = buildTransitions();

    private static Map<JobStatus, Set<JobStatus>> buildTransitions() {
        EnumMap<JobStatus, Set<JobStatus>> m = new EnumMap<>(JobStatus.class);
        m.put(JobStatus.CREATED, set(JobStatus.QUEUED, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.PAUSED, JobStatus.SKIPPED, JobStatus.CANCELLED, JobStatus.SUCCESS, JobStatus.FAILED));
        m.put(JobStatus.QUEUED, set(JobStatus.WAITING, JobStatus.RUNNING, JobStatus.PAUSED, JobStatus.STOPPED, JobStatus.KILLED, JobStatus.CANCELLED, JobStatus.SKIPPED, JobStatus.FAILED));
        m.put(JobStatus.WAITING, set(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.PAUSED, JobStatus.STOPPED, JobStatus.KILLED, JobStatus.CANCELLED, JobStatus.SKIPPED, JobStatus.FAILED));
        m.put(JobStatus.RUNNING, set(JobStatus.WAITING, JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.KILLED, JobStatus.STOPPED, JobStatus.PAUSED, JobStatus.RETRY_WAIT, JobStatus.TIMEOUT, JobStatus.CANCELLED));
        m.put(JobStatus.PAUSED, set(JobStatus.RUNNING, JobStatus.WAITING, JobStatus.STOPPED, JobStatus.KILLED, JobStatus.CANCELLED));
        m.put(JobStatus.RETRY_WAIT, set(JobStatus.QUEUED, JobStatus.WAITING, JobStatus.FAILED, JobStatus.CANCELLED));
        m.put(JobStatus.SUCCESS, set());
        m.put(JobStatus.FAILED, set());
        m.put(JobStatus.KILLED, set());
        m.put(JobStatus.STOPPED, set());
        m.put(JobStatus.TIMEOUT, set());
        m.put(JobStatus.SKIPPED, set());
        m.put(JobStatus.CANCELLED, set());
        m.put(JobStatus.HALTED, set(JobStatus.WAITING, JobStatus.RUNNING, JobStatus.CANCELLED, JobStatus.SKIPPED));
        return m;
    }

    private static Set<JobStatus> set(JobStatus... values) { if (values.length == 0) return EnumSet.noneOf(JobStatus.class); return EnumSet.copyOf(Arrays.asList(values)); }

    @Transactional
    public JobExecution transition(JobExecution execution, JobStatus target, String message) {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(target, "target");
        JobExecution current = execution.getId() == null ? execution : executionRepository.findByIdForUpdate(execution.getId()).orElseThrow(() -> new NoSuchElementException("Execution not found: " + execution.getId()));
        JobStatus old = current.getStatus() == null ? JobStatus.CREATED : current.getStatus();
        if (old == target) {
            if (message != null && !message.isBlank()) current.setMessage(message);
            return executionRepository.save(current);
        }
        if (!ALLOWED.getOrDefault(old, Set.of()).contains(target)) {
            throw new IllegalStateException("Invalid execution state transition " + old + " -> " + target + " for execution " + execution.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        current.setStatus(target);
        if (target == JobStatus.RUNNING && current.getStartTime() == null) current.setStartTime(now);
        if (target == JobStatus.RETRY_WAIT) current.setEndTime(null);
        if (isTerminal(target)) { current.setEndTime(now); current.setRuntimeHold(false); current.setHoldReason(null); }
        if (message != null) current.setMessage(message);
        JobExecution saved = executionRepository.save(current);
        eventRepository.save(com.mybank.autostone.entity.JobRunEvent.builder()
                .jobRunId(saved.getId()).eventType("STATE_CHANGE").oldStatus(old.name()).newStatus(target.name())
                .message(message).actor("SYSTEM").createdAt(now).build());
        // Every child transition immediately propagates through all containing BOXes.
        if (saved.getJob().getBox() != null && saved.getRunId() != null) {
            recalculateAncestors(saved.getJob(), saved.getWorkflowRunId());
        }
        // Publish the authoritative transition so dependency release and other consumers
        // react to the same state change rather than re-implementing terminal-state logic.
        eventPublisher.publishEvent(new ExecutionStateChangedEvent(
                saved.getId(), saved.getJob().getId(), saved.getRunId(), saved.getWorkflowRunId(), old, target, saved.getJob().getBox() == null ? null : saved.getJob().getBox().getId(), saved.getJob().getJobType(), message));
        return saved;
    }

    @Transactional
    public JobExecution transitionWithExitCode(JobExecution execution, JobStatus target, String message, Integer exitCode) {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(target, "target");
        JobExecution current = execution.getId() == null ? execution : executionRepository.findByIdForUpdate(execution.getId()).orElseThrow(() -> new NoSuchElementException("Execution not found: " + execution.getId()));
        if (exitCode != null) current.setExitCode(exitCode);
        JobStatus old = current.getStatus() == null ? JobStatus.CREATED : current.getStatus();
        if (old != target && !ALLOWED.getOrDefault(old, Set.of()).contains(target)) {
            throw new IllegalStateException("Invalid execution state transition " + old + " -> " + target + " for execution " + execution.getId());
        }
        if (old == target) {
            if (message != null && !message.isBlank()) current.setMessage(message);
            return executionRepository.save(current);
        }
        LocalDateTime now = LocalDateTime.now();
        current.setStatus(target);
        if (target == JobStatus.RUNNING && current.getStartTime() == null) current.setStartTime(now);
        if (target == JobStatus.RETRY_WAIT) current.setEndTime(null);
        if (isTerminal(target)) { current.setEndTime(now); current.setRuntimeHold(false); current.setHoldReason(null); }
        if (message != null) current.setMessage(message);
        JobExecution saved = executionRepository.save(current);
        eventRepository.save(JobRunEvent.builder().jobRunId(saved.getId()).eventType("STATE_CHANGE").oldStatus(old.name()).newStatus(target.name()).message(message).actor("SYSTEM").createdAt(now).build());
        if (saved.getJob().getBox() != null && saved.getRunId() != null) recalculateAncestors(saved.getJob(), saved.getWorkflowRunId());
        eventPublisher.publishEvent(new ExecutionStateChangedEvent(saved.getId(), saved.getJob().getId(), saved.getRunId(), saved.getWorkflowRunId(), old, target, saved.getJob().getBox() == null ? null : saved.getJob().getBox().getId(), saved.getJob().getJobType(), message));
        return saved;
    }

    /**
     * Operator tree actions sometimes need to change several descendants before any
     * ancestor is recalculated (for example KILL BOX). This keeps transition validation,
     * persistence and event recording centralized while temporarily deferring ancestor roll-up.
     */
    @Transactional
    public JobExecution transitionOperator(JobExecution execution, JobStatus target, String message) {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(target, "target");
        JobExecution current = execution.getId() == null ? execution : executionRepository.findByIdForUpdate(execution.getId()).orElseThrow(() -> new NoSuchElementException("Execution not found: " + execution.getId()));
        JobStatus old = current.getStatus() == null ? JobStatus.CREATED : current.getStatus();
        if (old == target) return execution;
        if (!ALLOWED.getOrDefault(old, Set.of()).contains(target)) {
            throw new IllegalStateException("Invalid execution state transition " + old + " -> " + target + " for execution " + execution.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        current.setStatus(target);
        if (target == JobStatus.RUNNING && current.getStartTime() == null) current.setStartTime(now);
        if (target == JobStatus.RETRY_WAIT) current.setEndTime(null);
        if (isTerminal(target)) { current.setEndTime(now); current.setRuntimeHold(false); current.setHoldReason(null); }
        if (message != null) current.setMessage(message);
        JobExecution saved = executionRepository.save(current);
        eventRepository.save(com.mybank.autostone.entity.JobRunEvent.builder()
                .jobRunId(saved.getId()).eventType("STATE_CHANGE").oldStatus(old.name()).newStatus(target.name())
                .message(message).actor("OPERATOR").createdAt(now).build());
        eventPublisher.publishEvent(new ExecutionStateChangedEvent(
                saved.getId(), saved.getJob().getId(), saved.getRunId(), saved.getWorkflowRunId(), old, target, saved.getJob().getBox() == null ? null : saved.getJob().getBox().getId(), saved.getJob().getJobType(), message));
        return saved;
    }

    @Transactional
    public void setRuntimeHold(JobExecution execution, boolean held, String reason) {
        execution.setRuntimeHold(held);
        execution.setHoldReason(held ? reason : null);
        executionRepository.save(execution);
    }

    public boolean isRuntimeHeld(JobExecution execution) {
        return execution != null && Boolean.TRUE.equals(execution.getRuntimeHold());
    }

    @Transactional
    public void recalculateAncestors(Job changedJob, UUID workflowRunId) {
        Job parent = changedJob.getBox();
        Set<Long> visited = new HashSet<>();
        while (parent != null && visited.add(parent.getId())) {
            recalculateBox(parent, workflowRunId);
            parent = parent.getBox();
        }
    }

    @Transactional(readOnly = true)
    public JobStatus deriveBoxStatus(Job box, UUID workflowRunId) {
        if (box == null || box.getJobType() != com.mybank.autostone.entity.JobType.BOX) return null;
        JobExecution boxExec = executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(box.getId(), workflowRunId).orElse(null);
        if (boxExec == null) return JobStatus.CREATED;
        if (boxExec.getStatus() == JobStatus.KILLED || boxExec.getStatus() == JobStatus.STOPPED || boxExec.getStatus() == JobStatus.CANCELLED) return boxExec.getStatus();
        if ((Boolean.TRUE.equals(box.getHold()) || Boolean.TRUE.equals(boxExec.getRuntimeHold())) && !isTerminal(boxExec.getStatus())) return JobStatus.WAITING;
        List<Job> children = jobRepository.findByBoxId(box.getId());
        if (children.isEmpty()) return JobStatus.SUCCESS;
        List<Long> childIds = children.stream().map(Job::getId).toList();
        Map<Long, JobExecution> latestByChild = executionRepository.findLatestForJobIdsAndWorkflowRunId(childIds, workflowRunId).stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getJob().getId(), e -> e, (a, b) -> a));
        List<JobExecution> states = children.stream().map(child -> latestByChild.get(child.getId())).toList();
        // Precedence is deliberate: an actually executing required child makes the BOX
        // RUNNING even when another child has not yet become eligible.
        // A child execution may legitimately not exist yet (for example while a
        // dependency is still waiting). Never dereference a null execution here:
        // dashboard refresh must remain healthy and the BOX should be shown as WAITING.
        // Live work keeps the BOX active. This is especially important when an
        // operator stops/kills one child while sibling work is still executing.
        if (states.stream().anyMatch(e -> e != null && e.getStatus() == JobStatus.RUNNING)) return JobStatus.RUNNING;
        if (states.stream().anyMatch(e -> e != null && e.getStatus() == JobStatus.PAUSED)) return JobStatus.PAUSED;
        if (states.stream().anyMatch(e -> e != null && (e.getStatus() == JobStatus.WAITING || e.getStatus() == JobStatus.QUEUED || e.getStatus() == JobStatus.RETRY_WAIT || e.getStatus() == JobStatus.HALTED))) return JobStatus.WAITING;
        // Once no child is live, preserve explicit operator outcomes. Natural
        // command failures/timeouts roll up to FAILED; STOP/KILL/CANCEL stay distinct.
        if (states.stream().anyMatch(e -> e != null && e.getStatus() == JobStatus.KILLED)) return JobStatus.KILLED;
        if (states.stream().anyMatch(e -> e != null && e.getStatus() == JobStatus.STOPPED)) return JobStatus.STOPPED;
        if (states.stream().anyMatch(e -> e != null && e.getStatus() == JobStatus.CANCELLED)) return JobStatus.CANCELLED;
        if (states.stream().anyMatch(e -> e != null && (e.getStatus() == JobStatus.FAILED || e.getStatus() == JobStatus.TIMEOUT))) return JobStatus.FAILED;
        if (states.stream().anyMatch(Objects::isNull)) return JobStatus.WAITING;
        if (states.stream().allMatch(e -> e != null && (e.getStatus() == JobStatus.SUCCESS || e.getStatus() == JobStatus.SKIPPED))) return JobStatus.SUCCESS;
        return boxExec.getStatus();
    }

    @Transactional
    public JobStatus recalculateBox(Job box, UUID workflowRunId) {
        JobExecution boxExec = executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(box.getId(), workflowRunId).orElse(null);
        if (boxExec == null) return null;
        JobStatus target = deriveBoxStatus(box, workflowRunId);
        if (target != null && target != boxExec.getStatus()) {
            transitionIfValid(boxExec, target, "BOX state derived from child execution state");
        }
        return boxExec.getStatus();
    }

    private void transitionIfValid(JobExecution e, JobStatus target, String message) {
        if (e.getStatus() == target) return;
        if (ALLOWED.getOrDefault(e.getStatus(), Set.of()).contains(target)) transition(e, target, message);
    }

    public boolean isTerminal(JobStatus s) {
        return s == JobStatus.SUCCESS || s == JobStatus.FAILED || s == JobStatus.STOPPED || s == JobStatus.KILLED || s == JobStatus.TIMEOUT || s == JobStatus.SKIPPED || s == JobStatus.CANCELLED;
    }
}
