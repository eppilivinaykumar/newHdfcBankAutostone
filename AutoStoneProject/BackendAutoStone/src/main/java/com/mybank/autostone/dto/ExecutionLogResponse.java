package com.mybank.autostone.dto;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExecutionLogResponse { private Long executionId; private String stdout; private String stderr; }
