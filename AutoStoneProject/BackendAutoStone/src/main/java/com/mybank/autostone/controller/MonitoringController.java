package com.mybank.autostone.controller;

import com.mybank.autostone.dto.MonitoringAlertResponse;
import com.mybank.autostone.dto.MonitoringJobPageResponse;
import com.mybank.autostone.entity.Alert;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobStatus;
import com.mybank.autostone.repository.AlertRepository;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.service.MonitoringService;
import com.mybank.autostone.dto.ExecutionTimelineResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {
    private final AlertRepository alertRepository;
    private final JobExecutionRepository executionRepository;
    private final MonitoringService monitoringService;


    @GetMapping("/jobs")
    public MonitoringJobPageResponse jobs(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) Long boxId,
            @RequestParam(defaultValue = "false") boolean rootsOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "250") int size) {
        return monitoringService.jobs(search, status, boxId, rootsOnly, page, size);
    }

    @GetMapping("/timeline")
    public List<ExecutionTimelineResponse> timeline(
            @RequestParam(required = false) Long jobId,
            @RequestParam(defaultValue = "500") int limit) {
        return monitoringService.timeline(jobId, limit);
    }

    @GetMapping("/alerts")
    public List<MonitoringAlertResponse> alerts() {
        List<MonitoringAlertResponse> stored = alertRepository.findTop100ByOrderByCreateTimeDesc().stream().map(this::mapAlert).toList();
        if (!stored.isEmpty()) return stored;
        return executionRepository.findLatestPerJob().stream()
                .filter(e -> e.getStatus() == JobStatus.FAILED || e.getStatus() == JobStatus.TIMEOUT || e.getStatus() == JobStatus.HALTED || e.getStatus() == JobStatus.KILLED)
                .sorted(Comparator.comparing(e -> e.getEndTime() != null ? e.getEndTime() : e.getStartTime(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100).map(this::mapExecution).toList();
    }

    private MonitoringAlertResponse mapAlert(Alert a) {
        JobExecution e = a.getJobRunId() == null ? null : executionRepository.findById(a.getJobRunId()).orElse(null);
        return MonitoringAlertResponse.builder().executionId(e == null ? null : e.getId()).jobId(e == null ? null : e.getJob().getId())
                .jobName(e == null ? a.getTitle() : e.getJob().getName()).type(reason(a.getAlertReason()))
                .status(e == null ? "ALERT" : e.getStatus().name()).message(a.getContent()).host(e == null ? null : e.getHost())
                .occurredAt(a.getCreateTime()).build();
    }
    private MonitoringAlertResponse mapExecution(JobExecution e) { return MonitoringAlertResponse.builder().executionId(e.getId()).jobId(e.getJob().getId()).jobName(e.getJob().getName()).type(e.getStatus().name()).status(e.getStatus().name()).message(e.getMessage()).host(e.getHost()).occurredAt(e.getEndTime()).build(); }
    private String reason(Integer r){return r==null?"ALERT":switch(r){case 1->"FAILURE";case 2->"TIMEOUT";case 3->"SERVER_UNAVAILABLE";default->"ALERT";};}
}
