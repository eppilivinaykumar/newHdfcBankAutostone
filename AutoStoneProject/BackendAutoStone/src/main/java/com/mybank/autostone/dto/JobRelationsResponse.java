package com.mybank.autostone.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobRelationsResponse {
    private List<JobSummaryResponse> parents;
    private List<JobSummaryResponse> children;
}
