package com.mybank.autostone.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_as_job_run_event", indexes = {
        @Index(name = "idx_run_event_created", columnList = "created_at,id"),
        @Index(name = "idx_run_event_run", columnList = "job_run_id,created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobRunEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_run_id", nullable = false) private Long jobRunId;
    @Column(name = "event_type", nullable = false, length = 50) private String eventType;
    @Column(name = "old_status", length = 30) private String oldStatus;
    @Column(name = "new_status", length = 30) private String newStatus;
    @Column(length = 2000) private String message;
    @Column(length = 128) private String actor;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
