package com.mybank.autostone.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_as_alert", indexes = {
        @Index(name = "idx_as_alert_sign", columnList = "sign"),
        @Index(name = "idx_as_alert_run", columnList = "job_run_id"),
        @Index(name = "idx_as_alert_time", columnList = "create_time")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String title;
    @Column(columnDefinition = "TEXT") private String content;
    @Column(length = 40) private String sign;
    @Builder.Default private Integer alertStatus = 0;
    private Integer alertReason;
    private Long jobRunId;
    private Long alertGroupId;
    private LocalDateTime createTime;
}
