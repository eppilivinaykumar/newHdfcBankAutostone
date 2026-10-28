package com.mybank.autostone.service;

import com.mybank.autostone.dto.*;
import com.mybank.autostone.entity.*;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MonitoringService {
    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final ExecutionStateService stateService;

    @Transactional(readOnly = true)
    public MonitoringJobPageResponse jobs(String search, String status, Long boxId, boolean rootsOnly, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 1000);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "name"));
        String q = search == null ? "" : search.trim();
        String requestedStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);

        if ("ALL".equals(requestedStatus)) {
            Page<Job> result = loadDefinitionPage(q, boxId, rootsOnly, pageable);
            return pageResponseForScope(result.getContent(), result.getTotalElements(), safePage, safeSize, boxId);
        }

        // Status is runtime-derived for BOXes, so it cannot safely be applied before
        // the latest execution/BOX roll-up is known. Filter the exact definition scope
        // first, then paginate the matching runtime states. This avoids the old bug where
        // page 0 could contain no RUNNING jobs even though RUNNING jobs existed later.
        List<Job> candidates = loadDefinitionList(q, boxId, rootsOnly);
        Map<Long, JobExecution> latestCandidates = candidates.isEmpty() ? Map.of() : executionRepository.findLatestForJobIds(candidates.stream().map(Job::getId).toList()).stream()
                .collect(Collectors.toMap(e -> e.getJob().getId(), e -> e, (a,b) -> a));
        UUID workflowRunId = workflowRunForBox(boxId);
        Map<Long, JobExecution> scopedCandidates = workflowRunId == null || candidates.isEmpty()
                ? latestCandidates
                : executionRepository.findLatestForJobIdsAndWorkflowRunId(candidates.stream().map(Job::getId).toList(), workflowRunId).stream()
                    .collect(Collectors.toMap(e -> e.getJob().getId(), e -> e, (a,b) -> a));
        List<Job> matching = candidates.stream()
                .filter(j -> requestedStatus.equals(toDashboard(j, scopedCandidates.get(j.getId())).getStatus()))
                .sorted(Comparator.comparing(Job::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int from = Math.min(safePage * safeSize, matching.size());
        int to = Math.min(from + safeSize, matching.size());
        return pageResponse(matching.subList(from, to), matching.size(), safePage, safeSize);
    }

    private Page<Job> loadDefinitionPage(String q, Long boxId, boolean rootsOnly, Pageable pageable) {
        if (boxId != null) return q.isBlank() ? jobRepository.findByBoxId(boxId, pageable) : jobRepository.findByBoxIdAndNameContainingIgnoreCase(boxId, q, pageable);
        if (rootsOnly) return q.isBlank() ? jobRepository.findByBoxIsNull(pageable) : jobRepository.findByBoxIsNullAndNameContainingIgnoreCase(q, pageable);
        return q.isBlank() ? jobRepository.findAll(pageable) : jobRepository.findByNameContainingIgnoreCase(q, pageable);
    }

    private List<Job> loadDefinitionList(String q, Long boxId, boolean rootsOnly) {
        if (boxId != null) return (q.isBlank() ? jobRepository.findByBoxId(boxId) : jobRepository.findByBoxId(boxId).stream().filter(j -> j.getName().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).toList());
        if (rootsOnly) return (q.isBlank() ? jobRepository.findByBoxIsNull(Pageable.unpaged()).getContent() : jobRepository.findByBoxIsNull(Pageable.unpaged()).getContent().stream().filter(j -> j.getName().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).toList());
        return q.isBlank() ? jobRepository.findAll() : jobRepository.findByNameContainingIgnoreCase(q, Pageable.unpaged()).getContent();
    }

    private MonitoringJobPageResponse pageResponse(List<Job> jobs, long total, int page, int size) {
        return pageResponseForScope(jobs, total, page, size, null);
    }

    private MonitoringJobPageResponse pageResponseForScope(List<Job> jobs, long total, int page, int size, Long boxId) {
        UUID workflowRunId = workflowRunForBox(boxId);
        Map<Long, JobExecution> latest = jobs.isEmpty() ? Map.of() : (
                workflowRunId == null
                        ? executionRepository.findLatestForJobIds(jobs.stream().map(Job::getId).toList())
                        : executionRepository.findLatestForJobIdsAndWorkflowRunId(jobs.stream().map(Job::getId).toList(), workflowRunId)
        ).stream().collect(Collectors.toMap(e -> e.getJob().getId(), e -> e, (a,b) -> a));
        List<DashboardJobResponse> content = jobs.stream().map(j -> toDashboard(j, latest.get(j.getId()))).toList();
        int totalPages = (int) Math.ceil(total / (double) size);
        return MonitoringJobPageResponse.builder().content(content).page(page).size(size).totalElements(total).totalPages(totalPages)
                .last(page + 1 >= Math.max(1, totalPages)).build();
    }

    private UUID workflowRunForBox(Long boxId) {
        if (boxId == null) return null;
        return executionRepository.findFirstByJobIdOrderByIdDesc(boxId)
                .map(JobExecution::getWorkflowRunId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ExecutionTimelineResponse> timeline(Long jobId, int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 5000);
        List<JobExecution> executions = jobId == null ? executionRepository.findTop5000ByOrderByIdDesc() : executionRepository.findTop5000ByJob_IdOrderByIdDesc(jobId);
        return executions.stream().limit(safeLimit).map(this::timeline).toList();
    }

    private ExecutionTimelineResponse timeline(JobExecution e) {
        LocalDateTime start = e.getStartTime();
        LocalDateTime end = e.getEndTime();
        long seconds = start == null ? 0 : Duration.between(start, end == null ? LocalDateTime.now() : end).getSeconds();
        return ExecutionTimelineResponse.builder().executionId(e.getId()).jobId(e.getJob().getId()).jobName(e.getJob().getName())
                .boxName(e.getJob().getBox() == null ? null : e.getJob().getBox().getName()).runId(e.getRunId()).status(e.getStatus().name())
                .attempt(e.getAttempt()).startTime(start).endTime(end).durationSeconds(Math.max(0, seconds)).host(e.getHost()).message(e.getMessage()).build();
    }

    private DashboardJobResponse toDashboard(Job job, JobExecution latest) {
        String status;
        if (job.getJobType() == JobType.BOX && latest != null) status = stateService.deriveBoxStatus(job, latest.getWorkflowRunId()).name();
        else status = latest == null ? null : latest.getStatus().name();
        DashboardJobResponse.DashboardJobResponseBuilder b = DashboardJobResponse.builder()
                .id(job.getId()).name(job.getName()).jobType(job.getJobType().name())
                .boxId(job.getBox() == null ? null : job.getBox().getId()).hostName(job.getHostName())
                .triggerType(job.getTriggerType() == null ? null : job.getTriggerType().name()).cronExpression(job.getCronExpression())
                .enabled(job.getEnabled()).hold(Boolean.TRUE.equals(job.getHold())).ice(Boolean.TRUE.equals(job.getIce())).noExecution(Boolean.TRUE.equals(job.getNoExecution()))
                .runtimeHold(latest != null && Boolean.TRUE.equals(latest.getRuntimeHold()))
                .status(status).runId(latest == null ? null : latest.getRunId()).executionId(latest == null ? null : latest.getId())
                .startTime(latest == null ? null : latest.getStartTime()).endTime(latest == null ? null : latest.getEndTime())
                .durationSeconds(duration(latest)).exitCode(latest == null ? null : latest.getExitCode()).retryCount(latest == null ? null : latest.getAttempt());
        if (job.getJobType() == JobType.BOX && latest != null) {
            List<Job> children = jobRepository.findByBoxId(job.getId());
            b.childCount(children.size());
            b.runningChildren(countChild(children, latest.getRunId(), JobStatus.RUNNING));
            b.successfulChildren(countChild(children, latest.getRunId(), JobStatus.SUCCESS));
            b.failedChildren(countChild(children, latest.getRunId(), JobStatus.FAILED));
            b.waitingChildren(countChild(children, latest.getRunId(), JobStatus.WAITING) + countChild(children, latest.getRunId(), JobStatus.QUEUED));
            b.killedChildren(countChild(children, latest.getRunId(), JobStatus.KILLED));
        }
        return b.build();
    }

    private long countChild(List<Job> children, UUID runId, JobStatus status) {
        return children.stream().map(j -> executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(j.getId(), runId).orElse(null)).filter(Objects::nonNull).filter(e -> e.getStatus() == status).count();
    }
    private Long duration(JobExecution e) { if (e == null || e.getStartTime() == null) return null; return Math.max(0, Duration.between(e.getStartTime(), e.getEndTime() == null ? LocalDateTime.now() : e.getEndTime()).getSeconds()); }
}
