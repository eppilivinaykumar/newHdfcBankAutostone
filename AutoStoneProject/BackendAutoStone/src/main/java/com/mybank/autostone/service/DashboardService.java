package com.mybank.autostone.service;

import com.mybank.autostone.dto.DashboardJobResponse;
import com.mybank.autostone.dto.DashboardSummaryResponse;
import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.JobDependency;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobStatus;
import com.mybank.autostone.repository.JobDependencyRepository;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final ExecutionStateService stateService;

    public List<DashboardJobResponse> getDashboard() {
        List<Job> jobs = jobRepository.findAll();
        if (jobs.isEmpty()) return List.of();
        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        Map<Long, List<Long>> dependsOnByJobId = jobDependencyRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(d -> d.getJob().getId(), Collectors.mapping(d -> d.getDependsOnJob().getId(), Collectors.toList())));
        Map<Long, JobExecution> latestByJobId = jobExecutionRepository.findLatestPerJob().stream()
                .collect(Collectors.toMap(e -> e.getJob().getId(), e -> e, (a,b) -> a));
        return jobs.stream().map(job -> toDashboardResponse(job, dependsOnByJobId, latestByJobId)).toList();
    }

    public DashboardSummaryResponse getSummary() {
        List<DashboardJobResponse> jobs = getDashboard();
        List<JobExecution> executions = jobExecutionRepository.findTop5000ByOrderByIdDesc();
        long totalBoxes = jobs.stream().filter(j -> "BOX".equals(j.getJobType())).count();
        return DashboardSummaryResponse.builder()
                .totalJobs(jobs.stream().filter(j -> "JOB".equals(j.getJobType())).count())
                .totalBoxes(totalBoxes)
                .running(count(jobs, "RUNNING"))
                .executing(jobs.stream().filter(j -> Set.of("RUNNING", "QUEUED", "WAITING", "PAUSED", "RETRY_WAIT").contains(j.getStatus())).count())
                .queued(count(jobs, "QUEUED"))
                .successful(count(jobs, "SUCCESS"))
                .failed(count(jobs, "FAILED"))
                .killed(count(jobs, "KILLED"))
                .waiting(count(jobs, "WAITING"))
                .inactive(jobs.stream().filter(j -> j.getStatus() == null || "CREATED".equals(j.getStatus()) || "STOPPED".equals(j.getStatus())).count())
                .restarted(count(jobs, "RETRY_WAIT") + count(jobs, "RESTARTED"))
                .failedExecutions(executions.stream().filter(e -> e.getStatus() == JobStatus.FAILED || e.getStatus() == JobStatus.TIMEOUT).count())
                .recentExecutions(executions.size())
                .scheduled(jobs.stream().filter(j -> "CRON".equals(j.getTriggerType()) && Boolean.TRUE.equals(j.getEnabled())).count())
                .paused(count(jobs, "PAUSED"))
                .stopped(count(jobs, "STOPPED"))
                .neverRun(jobs.stream().filter(j -> j.getStatus() == null).count())
                .generatedAt(LocalDateTime.now()).build();
    }

    private long count(List<DashboardJobResponse> jobs, String status) { return jobs.stream().filter(j -> status.equals(j.getStatus())).count(); }

    private DashboardJobResponse toDashboardResponse(Job job, Map<Long, List<Long>> dependsOnByJobId, Map<Long, JobExecution> latestByJobId) {
        List<Long> dependsOnIds = dependsOnByJobId.getOrDefault(job.getId(), List.of());
        JobExecution latest = latestByJobId.get(job.getId());
        String status = latest == null ? (Boolean.TRUE.equals(job.getHold()) ? "PAUSED" : "CREATED") : latest.getStatus().name();
        if (job.getJobType() == com.mybank.autostone.entity.JobType.BOX && latest != null) status = stateService.deriveBoxStatus(job, latest.getWorkflowRunId()).name();
        return DashboardJobResponse.builder().id(job.getId()).name(job.getName()).jobType(job.getJobType().name())
                .boxId(job.getBox() != null ? job.getBox().getId() : null).hostName(job.getHostName())
                .triggerType(job.getTriggerType() != null ? job.getTriggerType().name() : null).cronExpression(job.getCronExpression())
                .enabled(job.getEnabled()).hold(Boolean.TRUE.equals(job.getHold())).ice(Boolean.TRUE.equals(job.getIce())).noExecution(Boolean.TRUE.equals(job.getNoExecution()))
                .runtimeHold(latest != null && Boolean.TRUE.equals(latest.getRuntimeHold()))
                .dependsOnJobIds(dependsOnIds).status(status).runId(latest == null ? null : latest.getRunId())
                .executionId(latest == null ? null : latest.getId()).startTime(latest == null ? null : latest.getStartTime()).endTime(latest == null ? null : latest.getEndTime())
                .durationSeconds(duration(latest)).exitCode(latest == null ? null : latest.getExitCode()).retryCount(latest == null ? null : latest.getAttempt()).build();
    }
    private Long duration(JobExecution e) { if (e == null || e.getStartTime() == null) return null; return Math.max(0, Duration.between(e.getStartTime(), e.getEndTime() == null ? LocalDateTime.now() : e.getEndTime()).getSeconds()); }
}
