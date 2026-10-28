package com.mybank.autostone.controller;
import com.mybank.autostone.dto.*;
import com.mybank.autostone.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/jobs") @RequiredArgsConstructor
public class JobController {
 private final JobService jobService; private final EventService eventService; private final FlowService flowService;
 @GetMapping public List<JobSummaryResponse> getAll(){return jobService.getAllJobs();}
 @GetMapping("/flow") public FlowResponse rootFlow(@RequestParam(defaultValue="BOX") String mode, @RequestParam(defaultValue="1") int depth){return flowService.getRootFlow(mode, depth);}
 @GetMapping("/{id}") public JobSummaryResponse get(@PathVariable Long id){return jobService.get(id);}
 @GetMapping("/{id}/relations") public JobRelationsResponse relations(@PathVariable Long id){return jobService.getRelations(id);}
 @GetMapping("/{id}/flow") public FlowResponse flow(@PathVariable Long id, @RequestParam(defaultValue="ALL") String mode, @RequestParam(defaultValue="10") int depth){return flowService.getFlow(id, mode, depth);}
 @PostMapping public ResponseEntity<JobSummaryResponse> create(@RequestBody JobRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(r));}
 @PutMapping("/{id}") public JobSummaryResponse update(@PathVariable Long id,@RequestBody JobRequest r){return jobService.updateJob(id,r);}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id){jobService.delete(id);return ResponseEntity.noContent().build();}
 @PostMapping("/{id}/run") public EventResponse run(@PathVariable Long id,@RequestBody(required=false) EventActionRequest r){return eventService.start(id,r);}
 @PostMapping("/{id}/stop") public EventResponse stop(@PathVariable Long id,@RequestBody(required=false) EventActionRequest r){return eventService.stop(id,r);}
 @PostMapping("/{id}/pause") public EventResponse pause(@PathVariable Long id,@RequestBody(required=false) EventActionRequest r){return eventService.pause(id,r);}
 @PostMapping("/{id}/resume") public EventResponse resume(@PathVariable Long id,@RequestBody(required=false) EventActionRequest r){return eventService.resume(id,r);}
 @PostMapping("/{id}/rerun") public EventResponse rerun(@PathVariable Long id,@RequestBody(required=false) EventActionRequest r){return eventService.restart(id,r);}
}
