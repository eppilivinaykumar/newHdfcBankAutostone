package com.mybank.autostone.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkerResponse {
    private String host;
    private long jobCount;
    private long runningCount;
    private String status;
    private String detail;
}
