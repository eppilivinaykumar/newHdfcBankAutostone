package com.mybank.autostone.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FlowResponse {
    private Long rootJobId;
    private String rootJobName;
    private String mode;
    private int depth;
    private List<FlowNodeResponse> nodes;
    private List<FlowEdgeResponse> edges;
}
