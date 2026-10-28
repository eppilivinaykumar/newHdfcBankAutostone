package com.mybank.autostone.controller;

import com.mybank.autostone.dto.JilImportRequest;
import com.mybank.autostone.dto.JilImportResult;
import com.mybank.autostone.service.JilParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jil")
@RequiredArgsConstructor
public class JilController {

    private final JilParserService jilParserService;

    /** Parses + validates only, never writes to the DB - drives the import preview in the UI. */
    @PostMapping("/validate")
    public ResponseEntity<JilImportResult> validate(@RequestBody JilImportRequest request) {
        return ResponseEntity.ok(jilParserService.validateAndMaybeSave(request.getJilText(), false));
    }

    /** Re-validates (never trust a stale preview) then saves the whole batch if still clean. */
    @PostMapping("/import")
    public ResponseEntity<JilImportResult> importJil(@RequestBody JilImportRequest request) {
        return ResponseEntity.ok(jilParserService.validateAndMaybeSave(request.getJilText(), true));
    }
}
