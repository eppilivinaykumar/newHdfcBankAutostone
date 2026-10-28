package com.mybank.autostone.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_as_job_definition", indexes = {
        @Index(name = "idx_as_job_box", columnList = "box_code"),
        @Index(name = "idx_as_job_machine", columnList = "machine_id"),
        @Index(name = "idx_as_job_trigger", columnList = "trigger_type,enabled"),
        @Index(name = "idx_as_job_host", columnList = "host_name")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code")
    private Long id;

    @Column(name = "version", nullable = false)
    @Builder.Default private Integer version = 1;

    @Column(name = "job_name", nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 20)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 20)
    private TriggerType triggerType;

    @Column(name = "cron_expression", length = 255)
    private String cronExpression;
    private String timezone;
    private LocalDateTime scheduleStartTime;
    private LocalDateTime scheduleEndTime;

    @Enumerated(EnumType.STRING)
    @Builder.Default private MisfirePolicy misfirePolicy = MisfirePolicy.FIRE_NOW;

    @Column(length = 2000)
    private String conditionExpression;

    @Column(name = "command", length = 5000)
    private String command;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "box_code", referencedColumnName = "code")
    private Job box;

    @Column(name = "machine_id")
    private Long machineId;

    /* Compatibility field used by the current UI/executor. The canonical DB model also
       retains machine_id and t_as_machine; host_name makes the current UI migration-safe. */
    @Column(name = "host_name", length = 255)
    private String hostName;

    private String owner;
    private String permission;
    @Column(name = "application_name", length = 255)
    private String applicationName;
    @Column(name = "group_name", length = 255)
    private String groupName;
    @Column(name = "run_calendar_name", length = 255)
    private String runCalendarName;
    @Column(name = "date_conditions")
    private Boolean dateConditions;
    @Column(name = "send_notification")
    private Boolean sendNotification;
    @Column(name = "alarm_if_terminated")
    private Boolean alarmIfTerminated;
    @Column(name = "avg_runtime")
    private Integer avgRuntime;
    private Long runCalendarId;
    private Long excludeCalendarId;
    private String startTimes;
    private String runWindow;
    private Integer priority;
    private Integer maxRunAlarmMin;
    private Boolean alarmIfFail;
    private String stdOutFile;
    private String stdErrFile;
    private Integer nRetrys;
    private String profile;

    @Enumerated(EnumType.STRING)
    @Builder.Default private ExecutionType executionType = ExecutionType.SSH_SCRIPT;

    private Boolean enabled;
    @Builder.Default private Boolean hold = false;
    /** AutoSys auto_hold definition: applied to an in-box job when its containing BOX starts. */
    @Column(name = "auto_hold")
    @Builder.Default private Boolean autoHold = false;
    @Builder.Default private Boolean ice = false;
    @Builder.Default private Boolean noExecution = false;
    @Builder.Default private Integer retryCount = 0;
    @Builder.Default private Integer retryIntervalSeconds = 60;
    @Builder.Default private Integer timeoutSeconds = 0;

    @Column(length = 2000) private String workingDirectory;
    @Column(length = 2000) private String arguments;

    @Enumerated(EnumType.STRING)
    @Builder.Default private ConcurrencyPolicy concurrencyPolicy = ConcurrencyPolicy.ALLOW;

    @Builder.Default private Integer releaseState = 1;
    @Column(name = "create_time")
    private LocalDateTime createdAt;
    @Column(name = "update_time")
    private LocalDateTime updatedAt;

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (version == null) version = 1;
        if (releaseState == null) releaseState = 1;
        if (enabled == null) enabled = true;
        if (hold == null) hold = false;
        if (autoHold == null) autoHold = false;
        if (ice == null) ice = false;
        if (noExecution == null) noExecution = false;
        if (retryCount == null) retryCount = 0;
        if (retryIntervalSeconds == null) retryIntervalSeconds = 60;
        if (timeoutSeconds == null) timeoutSeconds = 0;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = LocalDateTime.now(); }
}
