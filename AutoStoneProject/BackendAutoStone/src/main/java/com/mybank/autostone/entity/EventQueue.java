package com.mybank.autostone.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_as_event_queue", indexes = {
        @Index(name = "idx_as_event_priority", columnList = "priority,id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventQueue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_type", nullable = false, length = 30) private String eventType;
    @Column(name = "job_code", nullable = false) private Long jobCode;
    @Builder.Default private Integer priority = 0;
    private String requestedBy;
    @Column(columnDefinition = "TEXT") private String payload;
    private LocalDateTime createTime;
}
