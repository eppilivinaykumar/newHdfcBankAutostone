package com.mybank.autostone.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MonitoringAlertResponse {
    private Long executionId;
    private Long jobId;
    private String jobName;
    private String type;
    private String status;
    private String message;
    private String host;
    private LocalDateTime occurredAt;
}
