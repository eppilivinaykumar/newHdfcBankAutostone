package com.mybank.autostone.service;

import com.mybank.autostone.dto.DashboardJobResponse;
import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobRunEvent;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import com.mybank.autostone.repository.JobRunEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringStreamService {
    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final JobRunEventRepository runEventRepository;
    private final ExecutionStateService stateService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile long lastEventId = 0;
    private volatile LocalDateTime lastTick = LocalDateTime.now().minusSeconds(5);

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup); emitter.onTimeout(cleanup); emitter.onError(ex -> cleanup.run());
        try { emitter.send(SseEmitter.event().name("connected").data("ok")); } catch (IOException e) { cleanup.run(); }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${autostone.monitoring.sse-interval-ms:2000}")
    public void publishDelta() {
        if (emitters.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        Set<Long> changedIds = new LinkedHashSet<>();
        try {
            List<JobRunEvent> events = runEventRepository.findByIdGreaterThanOrderByIdAsc(lastEventId).stream().limit(5000).toList();
            for (JobRunEvent event : events) {
                if (event.getId() > lastEventId) lastEventId = event.getId();
                executionRepository.findById(event.getJobRunId()).ifPresent(execution -> {
                    collectWithAncestors(execution.getJob(), changedIds);
                });
            }
            // Also capture definition changes (hold/enable/etc.) made without an execution transition.
            jobRepository.findByUpdatedAtAfter(lastTick).forEach(j -> collectWithAncestors(j, changedIds));
            List<DashboardJobResponse> changed = changedIds.isEmpty() ? List.of() : snapshot(changedIds);
            List<StateChange> stateChanges = events.stream()
                    .filter(e -> e.getNewStatus() != null)
                    .map(this::toStateChange)
                    .toList();
            if (!changed.isEmpty() || !stateChanges.isEmpty()) {
                Snapshot payload = new Snapshot(changed, stateChanges, now);
                for (SseEmitter emitter : emitters) {
                    try { emitter.send(SseEmitter.event().name("snapshot").data(payload)); }
                    catch (IOException e) { emitters.remove(emitter); try { emitter.complete(); } catch (Exception ignored) {} }
                }
            }
        } catch (Exception e) { log.debug("Monitoring delta unavailable: {}", e.getMessage()); }
        lastTick = now;
    }

    private void collectWithAncestors(Job job, Set<Long> ids) {
        Job current = job;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current.getId())) {
            ids.add(current.getId());
            current = current.getBox();
        }
    }

    private List<DashboardJobResponse> snapshot(Set<Long> ids) {
        List<Job> jobs = jobRepository.findAllById(ids);
        if (jobs.isEmpty()) return List.of();
        Map<Long, JobExecution> latest = executionRepository.findLatestForJobIds(jobs.stream().map(Job::getId).toList()).stream()
                .collect(Collectors.toMap(e -> e.getJob().getId(), e -> e));
        return jobs.stream().map(j -> DashboardJobResponse.builder().id(j.getId()).name(j.getName()).jobType(j.getJobType().name())
                .boxId(j.getBox() == null ? null : j.getBox().getId()).hostName(j.getHostName()).triggerType(j.getTriggerType() == null ? null : j.getTriggerType().name())
                .cronExpression(j.getCronExpression()).enabled(j.getEnabled()).hold(Boolean.TRUE.equals(j.getHold())).ice(Boolean.TRUE.equals(j.getIce())).noExecution(Boolean.TRUE.equals(j.getNoExecution()))
                .runtimeHold(latest.get(j.getId()) != null && Boolean.TRUE.equals(latest.get(j.getId()).getRuntimeHold()))
                .status(latest.get(j.getId()) == null ? null : (j.getJobType() == com.mybank.autostone.entity.JobType.BOX ? stateService.deriveBoxStatus(j, latest.get(j.getId()).getWorkflowRunId()).name() : latest.get(j.getId()).getStatus().name())).runId(latest.get(j.getId()) == null ? null : latest.get(j.getId()).getRunId()).build()).toList();
    }

    private StateChange toStateChange(JobRunEvent event) {
        JobExecution execution = executionRepository.findById(event.getJobRunId()).orElse(null);
        if (execution == null || execution.getJob() == null) return new StateChange(event.getJobRunId(), null, null, null, event.getOldStatus(), event.getNewStatus(), null, event.getCreatedAt(), event.getMessage(), null, null);
        Job job = execution.getJob();
        String derived = job.getJobType() == com.mybank.autostone.entity.JobType.BOX && execution.getRunId() != null
                ? stateService.deriveBoxStatus(job, execution.getWorkflowRunId()).name() : event.getNewStatus();
        return new StateChange(execution.getId(), job.getId(), execution.getRunId(), execution.getStatus().name(), event.getOldStatus(), event.getNewStatus(), job.getBox() == null ? null : job.getBox().getId(), event.getCreatedAt(), event.getMessage(), job.getJobType().name(), job.getJobType() == com.mybank.autostone.entity.JobType.BOX ? derived : null);
    }

    public record StateChange(Long executionId, Long jobId, UUID runId, String status, String oldStatus, String newStatus, Long parentBoxId, LocalDateTime timestamp, String message, String jobType, String derivedStatus) {}
    public record Snapshot(List<DashboardJobResponse> jobs, List<StateChange> stateChanges, LocalDateTime generatedAt) {}
}
