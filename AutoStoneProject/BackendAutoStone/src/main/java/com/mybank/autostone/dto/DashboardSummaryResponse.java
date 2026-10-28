package com.mybank.autostone.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {
    private long totalJobs;
    private long totalBoxes;
    private long executing;
    private long failedExecutions;
    private long killed;
    private long waiting;
    private long inactive;
    private long restarted;
    private long recentExecutions;
    private long running;
    private long queued;
    private long successful;
    private long failed;
    private long scheduled;
    private long paused;
    private long stopped;
    private long neverRun;
    private LocalDateTime generatedAt;
}
