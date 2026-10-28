package com.mybank.autostone.dto;
import lombok.*;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class JobCallbackRequest { private UUID runId; private Long jobId; private Long executionId; private int exitCode; private String status; }
