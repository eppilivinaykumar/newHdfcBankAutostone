package com.mybank.autostone.dto;
import lombok.*;
import java.util.List;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkActionRequest { private List<Long> jobIds; private String action; private String triggeredBy; }
