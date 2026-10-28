package com.mybank.autostone.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRequest {

    private String name;
    private String description;
    private String jobType;           // "JOB" or "BOX"
    private String triggerType;       // "MANUAL" / "CRON" / "DEPENDENCY"
    private String cronExpression;
    private String timezone;
    private String scheduleStartTime;
    private String scheduleEndTime;
    private String misfirePolicy;
    private String conditionExpression;
    private String command;
    private String hostName;
    private Long boxId;               // null if standalone or is a box itself
    private List<Long> dependsOnJobIds; // empty/null unless triggerType = DEPENDENCY
    private Boolean enabled;
    private String executionType;
    private Integer retryCount;
    private Integer retryIntervalSeconds;
    private Integer timeoutSeconds;
    private String workingDirectory;
    private String arguments;
    private String concurrencyPolicy;
}
