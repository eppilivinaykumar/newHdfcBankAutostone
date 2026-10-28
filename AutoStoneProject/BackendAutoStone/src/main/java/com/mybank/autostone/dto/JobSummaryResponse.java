package com.mybank.autostone.dto;
import java.util.List;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobSummaryResponse {
    private Long id; private String name; private List<Long> dependsOnJobIds; private String jobType; private Long boxId; private String triggerType;
    private String cronExpression; private String timezone; private String scheduleStartTime; private String scheduleEndTime;
    private String misfirePolicy; private String description; private String command; private String hostName; private Boolean enabled;
    private String status; private Integer retryCount;
    private String owner; private String permission; private String applicationName; private String groupName;
    private String runCalendarName; private String startTimes; private Boolean dateConditions;
    private Boolean alarmIfFail; private Boolean alarmIfTerminated; private Boolean sendNotification;
    private Integer maxRunAlarmMin; private Integer avgRuntime; private String profile;
    private String stdOutFile; private String stdErrFile; private Integer nRetrys; private Integer retryIntervalSeconds; private Integer timeoutSeconds;
    private String executionType; private String concurrencyPolicy; private String conditionExpression;
    private Boolean hold; private Boolean autoHold; private Boolean runtimeHold; private Boolean ice; private Boolean noExecution; private String workingDirectory; private String arguments;
    private Integer version;
}
