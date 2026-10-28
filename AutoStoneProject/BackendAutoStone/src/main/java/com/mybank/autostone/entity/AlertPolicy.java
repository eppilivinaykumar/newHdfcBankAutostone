package com.mybank.autostone.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_as_alert_policy")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlertPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Builder.Default private Boolean enabled = true;
    @Builder.Default private Boolean alertOnFailure = true;
    @Builder.Default private Boolean alertOnTimeout = true;
    @Builder.Default private Boolean alertOnServerUnavailable = true;
    @Builder.Default private Integer maxRunMinutes = 30;
    private LocalDateTime updatedAt;
}
