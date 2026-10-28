package com.mybank.autostone.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardJobResponse {
    private Long id;
    private String name;
    private String jobType;
    private Long boxId;
    private String hostName;
    private String triggerType;
    private String cronExpression;
    private List<Long> dependsOnJobIds;
    private Boolean enabled;
    private Boolean hold;
    private Boolean ice;
    private Boolean noExecution;
    private Boolean runtimeHold;
    private String status;   // latest JobExecution.status, null if never run
    private UUID runId;      // latest run this job took part in, null if never run
    private Long executionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private Integer exitCode;
    private Integer retryCount;
    private long childCount;
    private long runningChildren;
    private long successfulChildren;
    private long failedChildren;
    private long waitingChildren;
    private long killedChildren;
}
