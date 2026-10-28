package com.mybank.autostone.controller;
import com.mybank.autostone.dto.*;
import com.mybank.autostone.service.ExecutionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ExecutionController {
 private final ExecutionQueryService service;
 @GetMapping("/jobs/{jobId}/executions") public List<ExecutionResponse> byJob(@PathVariable Long jobId){return service.byJob(jobId);}
 @GetMapping("/executions/{id}") public ExecutionResponse get(@PathVariable Long id){return service.get(id);}
 @GetMapping("/executions/{id}/logs") public ExecutionLogResponse logs(@PathVariable Long id, @RequestParam(defaultValue = "200000") int maxChars){return service.logs(id, maxChars);}
}
