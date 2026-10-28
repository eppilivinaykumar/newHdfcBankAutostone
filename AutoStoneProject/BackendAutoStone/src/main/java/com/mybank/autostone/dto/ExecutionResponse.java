package com.mybank.autostone.dto;

import com.mybank.autostone.entity.JobStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExecutionResponse {
    private Long id;
    private Long jobId;
    private String jobName;
    private UUID runId;
    private JobStatus status;
    private Integer attempt;
    private Integer exitCode;
    private String pid;
    private String host;
    private String message;
    private String logPath;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
