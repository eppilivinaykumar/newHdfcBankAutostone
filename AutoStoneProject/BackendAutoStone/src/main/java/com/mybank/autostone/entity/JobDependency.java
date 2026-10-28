package com.mybank.autostone.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "t_as_job_condition", indexes = {
        @Index(name = "idx_as_condition_job", columnList = "job_code"),
        @Index(name = "idx_as_condition_parent", columnList = "depends_on_code")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobDependency {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_code", referencedColumnName = "code", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_code", referencedColumnName = "code", nullable = false)
    private Job dependsOnJob;

    @Column(name = "condition_type", length = 20, nullable = false)
    @Builder.Default private String conditionType = "SUCCESS";
    private Integer exitCodeValue;
    @Column(length = 512) private String rawExpression;
}
