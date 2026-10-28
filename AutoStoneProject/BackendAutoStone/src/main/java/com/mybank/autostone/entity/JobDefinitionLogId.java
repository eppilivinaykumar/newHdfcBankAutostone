package com.mybank.autostone.entity;
import java.io.Serializable;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class JobDefinitionLogId implements Serializable { private Long code; private Integer version; }
