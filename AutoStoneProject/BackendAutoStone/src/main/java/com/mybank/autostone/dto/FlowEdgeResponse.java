package com.mybank.autostone.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FlowEdgeResponse {
    private Long from;
    private Long to;
    private String conditionType;
    private Integer exitCodeValue;
    private String rawExpression;
}
