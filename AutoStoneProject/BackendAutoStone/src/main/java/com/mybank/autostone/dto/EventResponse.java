package com.mybank.autostone.dto;

import com.mybank.autostone.entity.JobStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {

    private Long jobId;
    private Long executionId;
    private String jobName;
    private String jobType;
    private UUID runId;
    private JobStatus status;
    private String message;
    private LocalDateTime timestamp;

    // ids of downstream jobs that were also affected (halted/stopped) by this action
    private List<Long> affectedJobIds;
    private List<Long> affectedExecutionIds;
}
