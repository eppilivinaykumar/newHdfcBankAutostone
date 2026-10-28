package com.mybank.autostone.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FlowNodeResponse {
    private Long id;
    private String type;
    private Long executionId;
    private java.util.UUID runId;
    private String name;
    private String jobType;
    private Long boxId;
    private Long parentBoxId;
    private String hostName;
    private String status;
    private String triggerType;
    private Boolean enabled;
    private Boolean hold;
    private Boolean ice;
    private Boolean noExecution;
    private Boolean runtimeHold;
    private Integer version;
    private Integer level;
    private Integer childCount;
    private Boolean hasChildren;
}
