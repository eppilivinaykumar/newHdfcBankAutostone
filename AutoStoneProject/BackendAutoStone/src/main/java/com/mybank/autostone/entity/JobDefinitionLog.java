package com.mybank.autostone.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="t_as_job_definition_log")
@IdClass(JobDefinitionLogId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobDefinitionLog {
    @Id private Long code;
    @Id private Integer version;
    private String jobName;
    private String jobType;
    @Column(columnDefinition="TEXT") private String command;
    private Long boxCode;
    @Column(columnDefinition="TEXT") private String definitionSnapshot;
    private String operator;
    private LocalDateTime operateTime;
}
