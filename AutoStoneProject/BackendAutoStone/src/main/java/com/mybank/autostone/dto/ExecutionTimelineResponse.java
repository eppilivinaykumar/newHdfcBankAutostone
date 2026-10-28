package com.mybank.autostone.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExecutionTimelineResponse {
    private Long executionId;
    private Long jobId;
    private String jobName;
    private String boxName;
    private UUID runId;
    private String status;
    private Integer attempt;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private String host;
    private String message;
}
