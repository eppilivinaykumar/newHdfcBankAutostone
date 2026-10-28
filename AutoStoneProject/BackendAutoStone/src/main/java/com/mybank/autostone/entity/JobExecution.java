package com.mybank.autostone.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "t_as_job_run", indexes = {
        @Index(name = "idx_as_run_job", columnList = "job_code,id"),
        @Index(name = "idx_as_run_status", columnList = "status"),
        @Index(name = "idx_as_run_time", columnList = "start_time,end_time"),
        @Index(name = "idx_as_run_box", columnList = "box_run_id"),
        @Index(name = "idx_as_run_uuid", columnList = "run_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobExecution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_code", referencedColumnName = "code", nullable = false)
    private Job job;

    @Column(name = "job_version", nullable = false)
    @Builder.Default private Integer jobVersion = 1;

    @Column(name = "run_number", nullable = false)
    @Builder.Default private Integer runNumber = 1;

    @Column(name = "box_run_id") private Long boxRunId;
    @Column(name = "machine_id") private Long machineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    private Integer pid;
    private Integer exitCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Column(name = "std_out_path", length = 512) private String stdOutPath;
    @Column(name = "std_err_path", length = 512) private String stdErrPath;

    @Builder.Default private Integer retryCount = 0;

    /* Operational extension fields retained for the existing UI/callback contract. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "run_id", columnDefinition = "char(36)", length = 36)
    private UUID runId;

    /** Unique correlation id for this complete BOX/workflow tree. Each execution keeps its own run_id. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "workflow_run_id", columnDefinition = "char(36)", length = 36)
    private UUID workflowRunId;
    @Column(length = 5000) private String message;
    @Column(length = 255) private String host;
    @Builder.Default private Integer attempt = 1;
    @Column(length = 2000) private String logPath;

    /** Runtime hold is distinct from Job.hold (definition-time hold). */
    @Column(name = "runtime_hold", nullable = false)
    @Builder.Default private Boolean runtimeHold = false;
    @Column(name = "hold_reason", length = 40)
    private String holdReason;

    @PrePersist
    void beforeInsert() {
        if (runId == null) runId = UUID.randomUUID();
        if (workflowRunId == null) workflowRunId = runId;
    }

    @PreUpdate
    void beforeUpdate() {
        if (workflowRunId == null) workflowRunId = runId;
    }
}
