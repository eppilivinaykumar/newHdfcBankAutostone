package com.mybank.autostone.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JilImportResult {
    private boolean success;
    private List<String> errors;          // empty when success = true
    private List<String> createdJobNames; // preview names on validate-only, actually-saved names on import
}
