package com.mybank.autostone.dto;
import lombok.*;
import java.util.List;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkActionResponse { private int requested; private int succeeded; private int failed; private List<String> errors; }
