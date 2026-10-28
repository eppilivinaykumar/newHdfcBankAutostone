package com.mybank.autostone.controller;

import com.mybank.autostone.dto.EventActionRequest;
import com.mybank.autostone.dto.EventResponse;
import com.mybank.autostone.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.mybank.autostone.dto.BulkActionRequest;
import com.mybank.autostone.dto.BulkActionResponse;

/**
 * All endpoints are keyed by job_id (works for a JOB or a BOX row alike),
 * never by execution_id - the backend resolves the relevant JobExecution
 * row(s) internally. This keeps the frontend stateless: it just says
 * "act on job X" and does not need to track run_id/execution bookkeeping.
 */
@RestController
@RequestMapping("/event")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping("/start/{jobId}")
    public ResponseEntity<EventResponse> start(
            @PathVariable Long jobId,
            @RequestBody(required = false) EventActionRequest request) {
        return ResponseEntity.ok(eventService.start(jobId, request));
    }

    @PostMapping("/stop/{jobId}")
    public ResponseEntity<EventResponse> stop(
            @PathVariable Long jobId,
            @RequestBody(required = false) EventActionRequest request) {
        return ResponseEntity.ok(eventService.stop(jobId, request));
    }

    @PostMapping("/pause/{jobId}")
    public ResponseEntity<EventResponse> pause(
            @PathVariable Long jobId,
            @RequestBody(required = false) EventActionRequest request) {
        return ResponseEntity.ok(eventService.pause(jobId, request));
    }

    @PostMapping("/resume/{jobId}")
    public ResponseEntity<EventResponse> resume(
            @PathVariable Long jobId,
            @RequestBody(required = false) EventActionRequest request) {
        return ResponseEntity.ok(eventService.resume(jobId, request));
    }

    @PostMapping("/restart/{jobId}")
    public ResponseEntity<EventResponse> restart(
            @PathVariable Long jobId,
            @RequestBody(required = false) EventActionRequest request) {
        return ResponseEntity.ok(eventService.restart(jobId, request));
    }

    @PostMapping("/force-start/{jobId}")
    public ResponseEntity<EventResponse> forceStart(@PathVariable Long jobId, @RequestBody(required = false) EventActionRequest request) {
        return ResponseEntity.ok(eventService.forceStart(jobId, request));
    }

    @PostMapping("/hold/{jobId}")
    public ResponseEntity<EventResponse> hold(@PathVariable Long jobId) { return ResponseEntity.ok(eventService.setHold(jobId, true)); }

    @PostMapping("/off-hold/{jobId}")
    public ResponseEntity<EventResponse> offHold(@PathVariable Long jobId) { return ResponseEntity.ok(eventService.setHold(jobId, false)); }

    @PostMapping("/ice/{jobId}")
    public ResponseEntity<EventResponse> ice(@PathVariable Long jobId) { return ResponseEntity.ok(eventService.setIce(jobId, true)); }

    @PostMapping("/off-ice/{jobId}")
    public ResponseEntity<EventResponse> offIce(@PathVariable Long jobId) { return ResponseEntity.ok(eventService.setIce(jobId, false)); }

    @PostMapping("/no-execution/{jobId}")
    public ResponseEntity<EventResponse> noExecution(@PathVariable Long jobId) { return ResponseEntity.ok(eventService.setNoExecution(jobId, true)); }

    @PostMapping("/off-no-execution/{jobId}")
    public ResponseEntity<EventResponse> offNoExecution(@PathVariable Long jobId) { return ResponseEntity.ok(eventService.setNoExecution(jobId, false)); }

    @PostMapping("/release-resources/{jobId}")
    public ResponseEntity<EventResponse> releaseResources(@PathVariable Long jobId) { return ResponseEntity.ok(eventService.releaseResources(jobId)); }

    @PostMapping("/kill/{jobId}")
    public ResponseEntity<EventResponse> kill(@PathVariable Long jobId, @RequestBody(required = false) EventActionRequest request) { return ResponseEntity.ok(eventService.kill(jobId, request)); }

    @PostMapping("/skip/{jobId}")
    public ResponseEntity<EventResponse> skip(@PathVariable Long jobId, @RequestBody(required=false) EventActionRequest request) { return ResponseEntity.ok(eventService.skip(jobId, request)); }

    @PostMapping("/mark-success/{jobId}")
    public ResponseEntity<EventResponse> markSuccess(@PathVariable Long jobId, @RequestBody(required=false) EventActionRequest request) { return ResponseEntity.ok(eventService.markSuccess(jobId, request)); }

    @PostMapping("/mark-failure/{jobId}")
    public ResponseEntity<EventResponse> markFailure(@PathVariable Long jobId, @RequestBody(required=false) EventActionRequest request) { return ResponseEntity.ok(eventService.markFailure(jobId, request)); }

    @PostMapping("/send-event/{jobId}")
    public ResponseEntity<EventResponse> sendEvent(@PathVariable Long jobId, @RequestBody(required = false) EventActionRequest request) { return ResponseEntity.ok(eventService.sendEvent(jobId, request)); }

    @PostMapping("/cancel-event/{jobId}")
    public ResponseEntity<EventResponse> cancelEvent(@PathVariable Long jobId) { return ResponseEntity.ok(eventService.cancelEvent(jobId)); }

    @PostMapping("/bulk")
    public ResponseEntity<BulkActionResponse> bulk(@RequestBody BulkActionRequest request) {
        List<String> errors = new ArrayList<>();
        int success = 0;
        List<Long> ids = request == null || request.getJobIds() == null ? List.of() : request.getJobIds();
        String action = request == null || request.getAction() == null ? "" : request.getAction().trim().toLowerCase(Locale.ROOT);
        for (Long id : new LinkedHashSet<>(ids)) {
            try {
                EventActionRequest ar = EventActionRequest.builder().triggeredBy(request.getTriggeredBy()).build();
                switch (action) {
                    case "start" -> eventService.start(id, ar);
                    case "kill" -> eventService.kill(id, ar);
                    case "restart", "rerun" -> eventService.restart(id, ar);
                    case "skip" -> eventService.skip(id, ar);
                    case "hold" -> eventService.setHold(id, true);
                    default -> throw new IllegalArgumentException("Unsupported bulk action: " + action);
                }
                success++;
            } catch (Exception e) { errors.add(id + ": " + e.getMessage()); }
        }
        return ResponseEntity.ok(BulkActionResponse.builder().requested(ids.size()).succeeded(success).failed(errors.size()).errors(errors).build());
    }

    // schedule control is separate from execution control - disabling a
    // schedule never touches an in-flight JobExecution row
    @PostMapping("/schedule/enable/{jobId}")
    public ResponseEntity<EventResponse> enableSchedule(@PathVariable Long jobId) {
        return ResponseEntity.ok(eventService.scheduleEnable(jobId));
    }

    @PostMapping("/schedule/disable/{jobId}")
    public ResponseEntity<EventResponse> disableSchedule(@PathVariable Long jobId) {
        return ResponseEntity.ok(eventService.scheduleDisable(jobId));
    }
}
